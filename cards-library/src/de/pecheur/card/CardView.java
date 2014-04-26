package de.pecheur.card;


import java.util.ArrayList;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.Adapter;
import android.widget.AdapterView;

public class CardView extends AbsStack implements OnGestureListener, OnKeyListener {
	private static final String TAG = "CardView";
	private static final boolean DEBUG = true;

	/**
	 * Duration of a view's animated appearing
	 */
	private static final int APPEARING_DURATION = 250; // ms

	/**
	 * Duration of a view's animated disappearing
	 */
	private static final int DISCARD_DURATION = 250; // ms

	/**
	 * Time influencing factor of the up, down, mid view move
	 */
	private static final int SETTLE_INFLUENCE_DURATION = 200; // factor

	/**
	 * Maximum amount of time to spend for a view move.
	 */
	private static final int SETTLE_MAX_DURATION = 600; // ms

	/**
	 * Indicates that the view is in an idle, settled state.
	 */
	private static final int SCROLL_STATE_IDLE = 0;

	/**
	 * Indicates that the view is in the process of settling to a final
	 * position.
	 */
	private static final int SCROLL_STATE_SETTLING = 1;

	/**
	 * Indicates that the view is currently being dragged by the user.
	 */
	public static final int SCROLL_STATE_DRAGGING = 2;
	
	/**
	 * Makes sure that the size of recycled views does not exceed the 
	 * size of maximum visible views on screen. (This can happen if an adapter 
	 * does not recycle its views). This amount is also depend on 
	 * {@link DISCARD_DURATION} and {@link APPEARING_DURATION}. 
	 */
	private static final int RECYCLE_BIN_SIZE = 7; // ms

	

	private int mScrollState = SCROLL_STATE_IDLE;

	private static final int SETTLE_UP = -1;
	private static final int SETTLE_MID = 0;
	private static final int SETTLE_DOWN = 1;

	private View mSelectedView;
	private boolean mSelectedViewDetached;

	/**
	 * Determines speed during touch scrolling
	 */
	private float mBaseLineFlingVelocity;
	private float mFlingVelocityInfluence;
	private GestureDetector mGestureDetector;
	private RecycleBin mRecycleBin;
	private OnItemSettleListener mSettleListener;

	/**
	 * Interface definition for a callback to be invoked when an item in this
	 * AdapterView was moved up, or down.
	 */
	public interface OnItemSettleListener {
		/**
		 * Callback method to be invoked when an item in this AdapterView was
		 * moved up.
		 * <p>
		 * Implementers can call getItemAtPosition(position) if they need to
		 * access the data associated with the selected item.
		 * 
		 * @param parent
		 *            The AdapterView where the move happened.
		 * @param view
		 *            The view within the AdapterView that was moved (this will
		 *            be a view provided by the adapter)
		 * @param position
		 *            The position of the view in the adapter.
		 * @param id
		 *            The row id of the item that was moved.
		 */
		public void onItemUp(AdapterView<?> parent, View view, int position,
				long id);

		/**
		 * Callback method to be invoked when an item in this AdapterView was
		 * moved down.
		 * <p>
		 * Implementers can call getItemAtPosition(position) if they need to
		 * access the data associated with the selected item.
		 * 
		 * @param parent
		 *            The AdapterView where the move happened.
		 * @param view
		 *            The view within the AdapterView that was moved (this will
		 *            be a view provided by the adapter)
		 * @param position
		 *            The position of the view in the adapter.
		 * @param id
		 *            The row id of the item that was moved.
		 */
		public void onItemDown(AdapterView<?> parent, View view, int position,
				long id);
	}

	private static final Interpolator sInterpolator = new Interpolator() {
		public float getInterpolation(float t) {
			t -= 1.0f;
			return t * t * t * t * t + 1.0f;
		}
	};

	public CardView(Context context, AttributeSet attrs) {
		super(context, attrs, 0);
		init(context);

	}

	public CardView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	public CardView(Context context) {
		super(context);
		init(context);
	}

	private void init(Context context) {
		mGestureDetector = new GestureDetector(context, this, null, true);
		mGestureDetector.setIsLongpressEnabled(isLongClickable());
		mRecycleBin = new RecycleBin();

		setWillNotDraw(false);
		setClipChildren(false);

		float density = context.getResources().getDisplayMetrics().density;
		mBaseLineFlingVelocity = 2500.0f * density;
		mFlingVelocityInfluence = 2.0f;
		this.setFocusable(true);
		this.setFocusableInTouchMode(true); 
		this.setOnKeyListener(this);
	}

	@Override
	public void setAdapter(Adapter adapter) {
		// clean up recycle bin
		mRecycleBin.clear();
		
		if (adapter != null) {
			int viewTypeCount = adapter.getViewTypeCount();
			mRecycleBin.setViewTypeCount(viewTypeCount);
		}
		
		super.setAdapter(adapter);
	}

	public void setOnItemSettleListener(OnItemSettleListener listener) {
		mSettleListener = listener;
	}
	
	public OnItemSettleListener getOnItemSettleListener() {
		return mSettleListener;
	}
	

	/**
	 * intern method for setting the selection. The adapter need to be set.
	 * 
	 * @param position of the new selection
	 * @param rowId of the new selection
	 * @param discard the old selected view. If true, the method will handle the
	 * removing of the previous selected view.
	 */
	@Override
	protected void onSelectionChange(int position, long id) {
		// remove old view
		if (!mSelectedViewDetached && mSelectedPosition != INVALID_POSITION) {
			// start discard animation
			mSelectedView.animate()
					.setDuration(DISCARD_DURATION)
					.scaleX(2)
					.scaleY(2)
					.alpha(0)
					.setListener(new RemoveViewAfterAnimation(mSelectedView))
					.start();
		}

		// add new view
		if (position != INVALID_POSITION) {
			mSelectedView = obtainView(position);
			mSelectedViewDetached = false;	// reset flag

			ObjectAnimator.ofPropertyValuesHolder(mSelectedView,
					PropertyValuesHolder.ofFloat("scaleX", 0.5f, 1f),
					PropertyValuesHolder.ofFloat("scaleY", 0.5f, 1f),
					PropertyValuesHolder.ofFloat("alpha", 0f, 1f))
			.setDuration(APPEARING_DURATION).start();
		} else {
			mSelectedView = null;
		}

		requestLayout();

	}

	private View obtainView(int position) {
		View scrapView = mRecycleBin.getScrapView(position);

		View child = mAdapter.getView(position, scrapView, this);

		// Respect layout params that are already in the view. Otherwise make some up...
        // noinspection unchecked
		CardView.LayoutParams p = (CardView.LayoutParams) child.getLayoutParams();
        if (p == null) {
            p = new CardView.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT,
                    ViewGroup.LayoutParams.FILL_PARENT, 0);
        }
        p.viewType = mAdapter.getItemViewType(position);
		
		
		if (scrapView != null) {
			if (scrapView == child) {
				// adapter returned our view and we only need
				// to attach and reset attributes.
				attachViewToParent(child, 0, p);

				child.setTranslationY(0);
				child.setAlpha(1);
				return child;
			} else {
				// adapter returned another view, so we recycle
				// our prepared view again and continue handling
				// the new view.
				mRecycleBin.addScrapView(scrapView);
			}
		}

		// add new view
		addViewInLayout(child, 0, p);

		// measure new view
		int mChildWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
				getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
				MeasureSpec.EXACTLY);
		int mChildHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
				getMeasuredHeight() - getPaddingTop() - getPaddingBottom(),
				MeasureSpec.EXACTLY);
		measureChild(child, mChildWidthMeasureSpec, mChildHeightMeasureSpec);
		
		return child;
	}
	
	@Override
	public boolean onDown(MotionEvent e) {
		return false;
	}

	@Override
	public void setLongClickable(boolean isLongpressEnabled) {
		mGestureDetector.setIsLongpressEnabled(isLongpressEnabled);
	}
	
	@Override
	public boolean isLongClickable() {
		return mGestureDetector.isLongpressEnabled();
	}
	
	@Override
	public void onLongPress(MotionEvent e) {
		// todo selecting with blue frame
		OnItemLongClickListener listener = getOnItemLongClickListener();
		if (mSelectedView != null && listener != null) {
			listener.onItemLongClick(this, 
					mSelectedView, 
					mSelectedPosition,
					mSelectedRowId);
		} 
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		cancelLongPress();
		mScrollState = SCROLL_STATE_DRAGGING;

		float progress = Math.abs(mSelectedView.getTranslationY())/ mSelectedView.getHeight();
		float y = mSelectedView.getY() - distanceY;

		mSelectedView.setY(y);
		mSelectedView.setAlpha(1 - progress);
		
		return true;
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		float translationY = mSelectedView.getTranslationY();
		if (velocityY > 0 && translationY > 0) {
			smoothMoveTo(SETTLE_DOWN, velocityY);
		} else if (velocityY < 0 && translationY < 0) {
			smoothMoveTo(SETTLE_UP, velocityY);
		} else {
			smoothMoveTo(SETTLE_MID, velocityY);
		}
		return true;
	}

	private void smoothMoveTo(int target, float velocity) {
		mScrollState = SCROLL_STATE_SETTLING;

		int height = mSelectedView.getHeight();
		int y = mSelectedView.getTop() + height * target;

		final float pageDelta = (float) Math.abs(y - mSelectedView.getY()) / height;
		int duration = (int) (pageDelta * SETTLE_INFLUENCE_DURATION);

		velocity = Math.abs(velocity);
		if (velocity > 0) {
			duration += (duration / (velocity / mBaseLineFlingVelocity)) * mFlingVelocityInfluence;
		} else {
			duration += 100;
		}
		duration = Math.min(duration, SETTLE_MAX_DURATION);

		mSelectedView.animate()
				.setDuration(duration)
				.y(y)
				.alpha(target != SETTLE_MID ? 0 : 1)
				.setInterpolator(sInterpolator)
				.setListener( target == SETTLE_MID ? null : 
					new RemoveViewAfterAnimation(mSelectedView))
				.start();

		
		
		if (target != SETTLE_MID) {
			// set flag for handling the view detachment by ourself
			mSelectedViewDetached = true;

			// select next item
			if(mItemCount > 0) {
				// mSelectedPosition + 1 >= mItemCount
				mNextSelectedPosition = mSelectedPosition + 1 < mItemCount ? 
						mSelectedPosition + 1 : 0;
				mNextSelectedRowId = mAdapter.getItemId(mNextSelectedPosition);
			} else {
				mNextSelectedPosition = INVALID_POSITION;
				mNextSelectedRowId = INVALID_ROW_ID;
			}
			
			// keep the selected position, id and view for 
			// the listener, which is going to be called after 
			// we selected the next position.
			
			int position = mSelectedPosition;
			long id = mSelectedRowId;
			View view = mSelectedView;

			checkSelectionChanged();
			
			
			// notify settle listener
			if (mSettleListener != null) {
				if(target==SETTLE_UP) {
					mSettleListener.onItemUp(this, view, position, id);
				} else {
					mSettleListener.onItemDown(this, view, position, id);
				}
			}

		}
	}
	

	@Override
	public void onShowPress(MotionEvent e) {}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		if (DEBUG) Log.v(TAG, "onSingleTapUp");

		OnItemClickListener listener = getOnItemClickListener();
		if (mSelectedView != null && listener != null) {
			listener.onItemClick(this,
					mSelectedView, 
					mSelectedPosition,
					mSelectedRowId);
		}
		return false;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
		if (mAdapter == null | mItemCount == 0)
			return false;
		return mGestureDetector.onTouchEvent(event);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (mAdapter == null | mItemCount == 0)
			return false;

		mGestureDetector.onTouchEvent(event);

		switch (event.getAction()) {
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			if (mScrollState != SCROLL_STATE_SETTLING) {
				smoothMoveTo(SETTLE_MID, 0);
			}
			mScrollState = SCROLL_STATE_IDLE;
			break;
		}
		return true;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		setMeasuredDimension(getDefaultSize(0, widthMeasureSpec),
				getDefaultSize(0, heightMeasureSpec));

		int mChildWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
				getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
				MeasureSpec.EXACTLY);
		int mChildHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
				getMeasuredHeight() - getPaddingTop() - getPaddingBottom(),
				MeasureSpec.EXACTLY);

		for (int i = 0; i < getChildCount(); i++) {
			measureChild(getChildAt(i), mChildWidthMeasureSpec, mChildHeightMeasureSpec);
		}
	}

	@Override
	protected void onLayout(boolean c, int l, int t, int r, int b) {
		// center children in parent
		int parentWidth = getMeasuredWidth();
		int parentHeight = getMeasuredHeight();
		int childCount = getChildCount();

		for (int i = 0; i < childCount; i++) {
			View child = getChildAt(i);

			int childWidth = child.getMeasuredWidth();
			int childHeight = child.getMeasuredHeight();
			child.layout((parentWidth - childWidth) / 2,
					(parentHeight - childHeight) / 2,
					(parentWidth + childWidth) / 2,
					(parentHeight + childHeight) / 2);
		}
	}

	
	@Override
	public View getSelectedView() {
		return mSelectedView;
	}

	
	@Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new CardView.LayoutParams(getContext(), attrs);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof CardView.LayoutParams;
    }
	

	/**
	 * This AnimatorListenerAdapter detaches the view after the animation end
	 * and recylces it.
	 */
	private class RemoveViewAfterAnimation extends AnimatorListenerAdapter {
		private final View view;

		public RemoveViewAfterAnimation(View view) {
			this.view = view;
		}

		@Override
		public void onAnimationEnd(Animator animation) {
			// remove view from screen
			if (mRecycleBin.addScrapView(view)) {
				detachViewFromParent(view);
			} else {
				// recylce bin is full, so we discard
				// this view.
				removeViewInLayout(view);
			}
		}
	}

	/**
	 * The RecycleBin facilitates reuse of views across layouts.
	 */
	private class RecycleBin {
		private ArrayList<View>[] mScrapViews;
		private int mViewTypeCount;
		private ArrayList<View> mCurrentScrap;

		public void setViewTypeCount(int viewTypeCount) {
			if (viewTypeCount < 1) {
				throw new IllegalArgumentException(
						"Can't have a viewTypeCount < 1");
			}
			// noinspection unchecked
			@SuppressWarnings("unchecked")
			ArrayList<View>[] scrapViews = new ArrayList[viewTypeCount];
			for (int i = 0; i < viewTypeCount; i++) {
				scrapViews[i] = new ArrayList<View>();
			}
			mViewTypeCount = viewTypeCount;
			mCurrentScrap = scrapViews[0];
			mScrapViews = scrapViews;
		}

		public void clear() {
			if (mViewTypeCount == 1) {
				final ArrayList<View> scrap = mCurrentScrap;
				final int scrapCount = scrap.size();
				for (int i = 0; i < scrapCount; i++) {
					removeDetachedView(scrap.remove(scrapCount - 1 - i), false);
				}
			} else {
				final int typeCount = mViewTypeCount;
				for (int i = 0; i < typeCount; i++) {
					final ArrayList<View> scrap = mScrapViews[i];
					final int scrapCount = scrap.size();
					for (int j = 0; j < scrapCount; j++) {
						removeDetachedView(scrap.remove(scrapCount - 1 - j),
								false);
					}
				}
			}
			requestLayout();
		}

		/**
		 * @return A view from the ScrapViews collection. These are unordered.
		 */
		public View getScrapView(int position) {
			ArrayList<View> scrapViews;
			if (mViewTypeCount == 1) {
				scrapViews = mCurrentScrap;
				int size = scrapViews.size();
				if (size > 0) {
					return scrapViews.remove(size - 1);
				} else {
					return null;
				}
			} else {
				int whichScrap = mAdapter.getItemViewType(position);
				if (whichScrap >= 0 && whichScrap < mScrapViews.length) {
					scrapViews = mScrapViews[whichScrap];
					int size = scrapViews.size();
					if (size > 0) {
						return scrapViews.remove(size - 1);
					}
				}
			}
			return null;
		}

		/**
		 * Put a view into the ScapViews list. These views are unordered.
		 * 
		 * @param scrap The view to add
		 * @return true, if view was added, else the recycle bin is full.
		 */
		public boolean addScrapView(View scrap) {
			CardView.LayoutParams lp = (CardView.LayoutParams) scrap.getLayoutParams();
			if (lp == null) {
				return false;
			}
			
			if (mViewTypeCount == 1) {
				if (mCurrentScrap.size() < RECYCLE_BIN_SIZE) {
					mCurrentScrap.add(scrap);
					
					if (DEBUG) Log.v(TAG, "recycle view. Recylce bin size: "+
							mCurrentScrap.size());
					
					return true;
				} 
			} else {
				if (mScrapViews[lp.viewType].size() < RECYCLE_BIN_SIZE) {
					mScrapViews[lp.viewType].add(scrap);
					
					if (DEBUG) Log.v(TAG, "recycle view. Recylce bin size: "+
							mScrapViews[lp.viewType].size());
					
					return true;
				}
				
			}
			
			if (DEBUG) Log.v(TAG, "recycle bin is full. Maybe the adapter " +
					"does not recycle its views");
			
			return false;
		}
	}
	
	
	/**
     *CardView extends LayoutParams to provide a place to hold the view type.
     */
    public static class LayoutParams extends ViewGroup.LayoutParams {
        /**
         * View type for this view, as returned by
         * {@link android.widget.Adapter#getItemViewType(int) }
         */
        int viewType;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public LayoutParams(int w, int h, int viewType) {
            super(w, h);
            this.viewType = viewType;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }
    
    
    @Override
	public String toString() {
		return "CardView{"
				+ Integer.toHexString(System.identityHashCode(this))
				+ " selectedId=" + mSelectedRowId + " position=" + mSelectedPosition
				+ "}";
	}

	@Override
	public boolean onKey(View arg0, int arg1, KeyEvent arg2) {
		Log.d("p1", "onKey: "+ arg1);
		return true;
	}
}
