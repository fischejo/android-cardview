package de.pecheur.card;


import android.content.Context;
import android.database.DataSetObserver;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AbsSpinner;
import android.widget.Adapter;
import android.widget.AdapterView;

/**
 * AbsStack is an abstract ViewGroup based on AdapterView, which handles:
 * a) the single selection of an adapter item.
 * b) the empty view showing during an empty adapter
 * c) the selection listener
 * 
 * @author Johannes Fischer
 *
 */
public abstract class AbsStack extends AdapterView<Adapter> {
	private static final String TAG = "AbsStack";
    private static final boolean DEBUG = true;

	/**
     * Maximum amount of time to spend in {@link #findSyncPosition()}
     */
	static final int SYNC_MAX_DURATION_MILLIS = 100;
	
	int mSelectedPosition = INVALID_POSITION;
	long mSelectedRowId = INVALID_ROW_ID;
	
	int mNextSelectedPosition = INVALID_POSITION;
	long mNextSelectedRowId = INVALID_ROW_ID;
	
	int mItemCount;
	
	Adapter mAdapter;
	private DataSetObserver mDataSetObserver;
	private View mEmptyView;


	
	public AbsStack(Context context, AttributeSet attrs) {
		super(context, attrs, 0);
	}

	public AbsStack(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public AbsStack(Context context) {
		super(context);

	}
	

	@Override
	public void setAdapter(Adapter adapter) {
		if (null != mAdapter && null != mDataSetObserver) {
			if (DEBUG) Log.v(TAG, "unregister adapter "+mAdapter.toString());
			
			mAdapter.unregisterDataSetObserver(mDataSetObserver);
			mNextSelectedPosition = INVALID_POSITION;
			mNextSelectedRowId = INVALID_ROW_ID;
		}

		mAdapter = adapter;
		
		if (mAdapter != null) {
			mDataSetObserver = new AdapterDataSetObserver();
			mAdapter.registerDataSetObserver(mDataSetObserver);
			mItemCount = mAdapter.getCount();
			
			if (DEBUG) Log.v(TAG, "register adapter "+adapter.toString()+". item count: "+ mItemCount);
		
			if (mItemCount > 0) {
				mNextSelectedPosition = 0;
				mNextSelectedRowId = mAdapter.getItemId(0);
			}
		} 
		
		checkSelectionChanged();
		updateEmptyStatus();
	}

	
	@Override
	public Adapter getAdapter() {
		return mAdapter;
	}

	@Override
	public void setEmptyView(View emptyView) {
		mEmptyView = emptyView;
		updateEmptyStatus();
	}
	
	@Override
	public View getEmptyView() {
		return mEmptyView;
	}
	
	@Override
	public int getSelectedItemPosition() {
		return mSelectedPosition;
	}

	@Override
	public long getSelectedItemId() {
		return mSelectedRowId;
	}
	
	@Override
	public int getCount() {
		return mItemCount;
	}
	
	
	@Override
	public void setSelection(int position) {
		if (mAdapter != null && position < mItemCount && position >= 0) {
			mNextSelectedPosition = position;
			mNextSelectedRowId = mAdapter.getItemId(position);
			checkSelectionChanged();
		}
	}
	
	
	protected void checkSelectionChanged() {
        if ((mSelectedPosition != mNextSelectedPosition) || 
        		(mSelectedRowId != mNextSelectedRowId)
        		|| mItemCount == 1) {
        	onSelectionChange(mNextSelectedPosition, mNextSelectedRowId);
        	
        	if (DEBUG) Log.v(TAG, "selection changes to position: "+
    				mNextSelectedPosition+", id: "+mNextSelectedRowId);
    		
    		mSelectedPosition = mNextSelectedPosition;
    		mSelectedRowId = mNextSelectedRowId; 
    		
            fireOnSelected();
        } else {
        	if (DEBUG) Log.v(TAG, "selection did'nt change");
        }
    }
	
	
	private void fireOnSelected() {
		// notify listener
		OnItemSelectedListener listener = getOnItemSelectedListener();
		if (listener != null) {
			if (mSelectedPosition == INVALID_POSITION) {
				listener.onNothingSelected(this);
			} else {
				listener.onItemSelected(this, 
						getSelectedView(), 
						mSelectedPosition,
						mSelectedRowId);
			}
		}
	}
	
	protected abstract void onSelectionChange(int position, long id);
	
	
	
	/**
	 * Update the status of the list based on the empty parameter. If empty is
	 * true and we have an empty view, display it. In all the other cases, make
	 * sure that the listview is VISIBLE and that the empty view is GONE (if
	 * it's not null).
	 */
	private void updateEmptyStatus() {
		mEmptyView.setVisibility(
				mAdapter == null || mItemCount == 0 ?
				View.VISIBLE : // empty view visible
				View.GONE); // empty view invisible
	}
	
	static class SavedState extends BaseSavedState {
		long selectedId;
		int position;

		/**
		 * Constructor called from {@link AbsSpinner#onSaveInstanceState()}
		 */
		SavedState(Parcelable superState) {
			super(superState);
		}

		/**
		 * Constructor called from {@link #CREATOR}
		 */
		private SavedState(Parcel in) {
			super(in);
			selectedId = in.readLong();
			position = in.readInt();
		}

		@Override
		public void writeToParcel(Parcel out, int flags) {
			super.writeToParcel(out, flags);
			out.writeLong(selectedId);
			out.writeInt(position);
		}

		@Override
		public String toString() {
			return "AbsStack.SavedState{"
					+ Integer.toHexString(System.identityHashCode(this))
					+ " selectedId=" + selectedId + " position=" + position
					+ "}";
		}

		public static final Parcelable.Creator<SavedState> CREATOR = new Parcelable.Creator<SavedState>() {
			public SavedState createFromParcel(Parcel in) {
				return new SavedState(in);
			}

			public SavedState[] newArray(int size) {
				return new SavedState[size];
			}
		};
	}

	
	@Override
	public Parcelable onSaveInstanceState() {
		Parcelable superState = super.onSaveInstanceState();
		
		if (DEBUG) Log.v(TAG, "save instance state.");
		
		SavedState ss = new SavedState(superState);
		ss.selectedId = mSelectedRowId;
		if (ss.selectedId >= 0) {
			ss.position = mSelectedPosition;
		} else {
			ss.position = AdapterView.INVALID_POSITION;
		}
		return ss;
	}

	@Override
	public void onRestoreInstanceState(Parcelable state) {
		SavedState ss = (SavedState) state;
		super.onRestoreInstanceState(ss.getSuperState());
		
		if (DEBUG) Log.v(TAG, "restore instance state.");
		
		mNextSelectedPosition = ss.position;
		mNextSelectedRowId = ss.selectedId;
		
		handleDataChanged();
		checkSelectionChanged();
	}
	
	
	private void handleDataChanged() {
		if (mItemCount > 0) {
			// See if we can find a position in the new data with the same
			// id as the old selection
			int  newPos = findSyncPosition();
			if (newPos != INVALID_POSITION) {
				if (DEBUG) Log.v(TAG, "handleDataChanged: resynced position to "+ newPos);
				
				// we found the new position with a similar id, so
				// we just change the selected position quietly.
				mSelectedPosition = newPos;
				
				// overwriting the next selected position avoids a call of
				// onSelectionChange.
				mNextSelectedPosition = newPos;
				mNextSelectedRowId = mSelectedRowId;
				
				return;
				
			// Try to use the next position if we can't find matching data
			} else if (mNextSelectedPosition != INVALID_POSITION && 
					mNextSelectedPosition < mItemCount) {
				
				if (DEBUG) Log.v(TAG, "handleDataChanged: select next item");
				mNextSelectedRowId = mAdapter.getItemId(mNextSelectedPosition);
				
			// Select first item.
			} else {
				if (DEBUG) Log.v(TAG, "handleDataChanged: select first item");
				mNextSelectedPosition = 0;
				mNextSelectedRowId = mAdapter.getItemId(0);
			}
		} else {
			mNextSelectedPosition = INVALID_POSITION;
			mNextSelectedRowId = INVALID_ROW_ID;
		}
	}
	
	
	 /**
     * Searches the adapter for a position matching mSyncRowId. The search starts at mSyncPosition
     * and then alternates between moving up and moving down until 1) we find the right position, or
     * 2) we run out of time, or 3) we have looked at every position
     *
     * @return Position of the row that matches mSyncRowId, or {@link #INVALID_POSITION} if it can't
     *         be found
     */
    private int findSyncPosition() {
        int count = mItemCount;

        long idToMatch = mSelectedRowId;
        int seed = mSelectedPosition;

        // If there isn't a selection don't hunt for it
        if (idToMatch == INVALID_ROW_ID) {
            return INVALID_POSITION;
        }

        // Pin seed to reasonable values
        seed = Math.max(0, seed);
        seed = Math.min(count - 1, seed);

        long endTime = SystemClock.uptimeMillis() + SYNC_MAX_DURATION_MILLIS;

        long rowId;

        // first position scanned so far
        int first = seed;

        // last position scanned so far
        int last = seed;

        // True if we should move down on the next iteration
        boolean next = false;

        // True when we have looked at the first item in the data
        boolean hitFirst;

        // True when we have looked at the last item in the data
        boolean hitLast;

        if (mAdapter == null) {
            return INVALID_POSITION;
        }

        while (SystemClock.uptimeMillis() <= endTime) {
            rowId = mAdapter.getItemId(seed);
            if (rowId == idToMatch) {
                // Found it!
                return seed;
            }

            hitLast = last == count - 1;
            hitFirst = first == 0;

            if (hitLast && hitFirst) {
                // Looked at everything
                break;
            }

            if (hitFirst || (next && !hitLast)) {
                // Either we hit the top, or we are trying to move down
                last++;
                seed = last;
                // Try going up next time
                next = false;
            } else if (hitLast || (!next && !hitFirst)) {
                // Either we hit the bottom, or we are trying to move up
                first--;
                seed = first;
                // Try going down next time
                next = true;
            }

        }

        return INVALID_POSITION;
    }

	private class AdapterDataSetObserver extends DataSetObserver {
		private Parcelable mInstanceState = null;

		@Override
		public void onChanged() {
			if (DEBUG) Log.v(TAG, "adapter data changed.");
			
			mItemCount = mAdapter.getCount();

			// Detect the case where a cursor that was previously invalidated
			// has been repopulated with new data.
			if (getAdapter().hasStableIds() && mInstanceState != null && mItemCount > 0) {
				onRestoreInstanceState(mInstanceState);
				mInstanceState = null;
			} else {
				handleDataChanged();
				checkSelectionChanged();
			}
			
			updateEmptyStatus();
		}

		@Override
		public void onInvalidated() {
			if (DEBUG) Log.v(TAG, "adapter invalid. Reset states.");
			if (getAdapter().hasStableIds()) {
				// Remember the current state for the case where our hosting
				// activity is being stopped and later restarted
				mInstanceState = onSaveInstanceState();
			}

			// Data is invalid so we should reset our state
			mItemCount = 0;
			mNextSelectedPosition = INVALID_POSITION;
			mNextSelectedRowId = INVALID_ROW_ID;
			
			checkSelectionChanged();
			updateEmptyStatus();

		}
	}
	

	@Override
	public String toString() {
		return "AbsStack{"
				+ Integer.toHexString(System.identityHashCode(this))
				+ " selectedId=" + mSelectedRowId + " position=" + mSelectedPosition
				+ "}";
	}
}
