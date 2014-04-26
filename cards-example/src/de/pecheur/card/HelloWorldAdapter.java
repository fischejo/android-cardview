package de.pecheur.card;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class HelloWorldAdapter extends BaseAdapter {
	private Context context;
	private static final int ITEM_TYPE_COUNT = 4;
	
	// it is important to use ids, if you are going to 
	// delete or add views, or the cardview is not 
	// going to find the selected card again after a change.
	private ArrayList<Integer> mIds;

	private ArrayList<String> mItems;
	
	
	public HelloWorldAdapter(Context context) {
		this.context = context;
		
		// load hello world string array from resources.
		String[] items = context.getResources().
				getStringArray(R.array.hello_worlds);
		
		mItems = new ArrayList<String>();
		
		// to guarantee stable ids:
		mIds = new ArrayList<Integer>();	
		int idCounter = 0;
		
		
		for(String item : items) {
			mItems.add(item);
			mIds.add(idCounter++);
		}
		
		notifyDataSetChanged();
	}
	
	@Override
	public String getItem(int position) {
		return mItems.get(position);
	}
	
	@Override
	public int getCount() {
		return mItems.size();
	}

	@Override
	public long getItemId(int position) {
		return mIds.get(position);
	}
	
	@Override
	public boolean hasStableIds() {
		return true;
	}
	
	 @Override
	public int getViewTypeCount() {
		 return ITEM_TYPE_COUNT;
	}
	 
	 @Override
	public int getItemViewType(int position) {
		 return mIds.get(position)%ITEM_TYPE_COUNT;
	}
	
	/**
	 * simple item remove implementation for
	 * demonstration purposes.
	 * @param position of item
	 */
	public void remove(int position) {
		mItems.remove(position);
		mIds.remove(position);
		notifyDataSetChanged();
	}
	

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		if (view == null) {
        	view = LayoutInflater.from(context)
        			.inflate(R.layout.card_view, parent, false);

    		switch( getItemViewType(position)) {
    		case 0:
    			view.setBackgroundResource(R.drawable.card_background_orange);
    			break;
    		case 1:
    			view.setBackgroundResource(R.drawable.card_background_green);
    			break;
    		case 2:
    			view.setBackgroundResource(R.drawable.card_background_blue);
    			break;
    		case 3:
    			view.setBackgroundResource(R.drawable.card_background_red);
    			break;
    		}
        }

    	TextView text = (TextView) view;
		text.setText( getItem(position));

        return view;
	}
}
