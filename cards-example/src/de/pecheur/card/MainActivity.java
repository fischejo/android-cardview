package de.pecheur.card;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import de.pecheur.card.CardView.OnItemSettleListener;


public class MainActivity extends Activity implements OnItemSettleListener {
	HelloWorldAdapter mAdapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		CardView cardView = (CardView) findViewById(R.id.cardView);
		
		// set empty view
		View emptyView = findViewById(R.id.emptyView);
		cardView.setEmptyView( emptyView);
		
		// set adapter
		mAdapter = new HelloWorldAdapter(this);
		cardView.setAdapter(mAdapter);
		
		// set settle listener
		cardView.setOnItemSettleListener(this);
	}

	@Override
	public void onItemUp(AdapterView<?> parent, View view, int position, long id) {
		// each card, which was moved up, is removed
		// from the stack.
		mAdapter.remove(position);
	}

	@Override
	public void onItemDown(AdapterView<?> parent, View view, int position,
			long id) {
		// TODO Auto-generated method stub
		
	}

}
