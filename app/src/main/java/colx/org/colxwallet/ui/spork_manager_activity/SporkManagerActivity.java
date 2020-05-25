package colx.org.colxwallet.ui.spork_manager_activity;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

import colx.org.colxwallet.R;
import colx.org.colxwallet.ui.base.BaseActivity;

public class SporkManagerActivity extends BaseActivity {
    @Override
    protected void onCreateView(Bundle savedInstanceState, ViewGroup container) {
        setTitle("Network sporks");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        ListView sporkListView = new ListView(this);
        sporkListView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ArrayList<String> sporkList = pivxModule.getSporkList();
        ArrayAdapter<String> stringArray = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, sporkList);
        sporkListView.setAdapter(stringArray);

        container.addView(sporkListView);
    }
}
