package com.example.michael.todolist;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.support.constraint.ConstraintLayout;
import android.support.constraint.ConstraintSet;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.text.Layout;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Created by Michael Purcell on 12/9/2017.
 *
 * All resources used for project:
 * https://stackoverflow.com/questions/27293960/swipe-to-dismiss-for-recyclerview
 * https://www.androidhive.info/2016/01/android-working-with-recycler-view/
 * https://developer.android.com/reference/android/support/v4/widget/SwipeRefreshLayout.html
 */

public class MainActivity extends AppCompatActivity{

    private static String TAG = "MainActivity";

    ToDoBroadcastReceiver toDoBroadcastReceiver;
    ControllerService controllerService = new ControllerService();
    private RecyclerView recyclerView;
    private ArrayList<String> titles;
    private ArrayList<ViewItem> items;
    private ViewItemAdapter viewItemAdapter;
    private View newItemView;
    private ItemTouchHelper.SimpleCallback itemSwipe;
    private AlertDialog.Builder builder;
    private View deleteItemView;
    private boolean askLater = true;
    private View modifyItemView;
    private SwipeRefreshLayout refreshLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        refreshLayout = findViewById(R.id.refreshLayout);
        recyclerView = findViewById(R.id.recyclerView);
        titles = new ArrayList<>();
        items = new ArrayList<>();
        viewItemAdapter = new ViewItemAdapter(this,items,R.layout.row_items);

        recyclerView.setAdapter(viewItemAdapter);
        RecyclerView.LayoutManager manager = new LinearLayoutManager(getApplicationContext());
        recyclerView.setLayoutManager(manager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new DividerItemDecoration(this, LinearLayout.VERTICAL));
        initItemTouchHelper();
        ItemTouchHelper helper = new ItemTouchHelper(itemSwipe);
        helper.attachToRecyclerView(recyclerView);

        refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                sortItems();
                refreshLayout.setRefreshing(false);
            }
        });

        initNewItemDialog();
        toDoBroadcastReceiver = new ToDoBroadcastReceiver();
        registerReceiver(toDoBroadcastReceiver,toDoBroadcastReceiver.getIntentFilter());

        startService(new Intent(this,controllerService.getClass()));

    }

    @Override
    protected void onStart() {
        super.onStart();
        items.clear();
        viewItemAdapter.notifyDataSetChanged();
        //Hacky way to make sure the ControllerService has been started before requesting all items.
        //Should change this later.
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                getApplicationContext().sendBroadcast(new Intent(ControllerService.ACTION_REQUEST_ALL_ITEMS));
            }
        },100);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.toolbar_main_menu,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id){
            case R.id.new_todo:
                builder.show();
                return true;
            case R.id.changeView:
                startActivity(new Intent(this,SecondActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(toDoBroadcastReceiver);
    }

    //Sorts the items so that there are one of each priority in the top three
    public void sortItems(){
        ArrayList<ViewItem> temp = new ArrayList<>();
        boolean foundLow = false;
        boolean foundMed = false;
        boolean foundHigh = false;

        for(int i = 0; i < items.size();i++){

            ViewItem item = items.get(i);
            String priority = item.getStringPriority();

            if(!foundLow && priority.equals("Low")){
                temp.add(item);
                items.remove(item);
                viewItemAdapter.notifyItemRemoved(i);
                foundLow = true;
            }
            else if(!foundMed && priority.equals("Medium")){
                temp.add(item);
                items.remove(item);
                viewItemAdapter.notifyItemRemoved(i);
                foundMed = true;
            }
            else if(!foundHigh && priority.equals("High")){
                temp.add(item);
                items.remove(item);
                viewItemAdapter.notifyItemRemoved(i);
                foundHigh = true;
            }
        }
        for(ViewItem item : temp) {
            items.add(0, item);
            viewItemAdapter.notifyItemInserted(0);
        }

        recyclerView.setScrollY(0);
        Log.d(TAG,"Done sorting");
    }

    private void initNewItemDialog(){

        builder = new AlertDialog.Builder(MainActivity.this);
        LayoutInflater inflater = getLayoutInflater();
        newItemView = inflater.inflate(R.layout.new_item,null);
        final EditText editTitle = newItemView.findViewById(R.id.editTitle);;
        final Spinner editPriority = newItemView.findViewById(R.id.editPriority);
        final EditText editDesc = newItemView.findViewById(R.id.editDesc);
        final EditText editShortDesc = newItemView.findViewById(R.id.editShortDesc);

        builder.setView(newItemView);
        builder.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if(keyCode == KeyEvent.KEYCODE_BACK){
                    dialog.dismiss();
                    ((ViewGroup)newItemView.getParent()).removeView(newItemView);
                    return true;
                }
                return false;
            }
        });
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                broadcastAddItem(editTitle,editPriority,editDesc,editShortDesc);

                editTitle.setText("");
                //priority.setText("");
                editDesc.setText("");
                editShortDesc.setText("");
                dialog.dismiss();
                ((ViewGroup)newItemView.getParent()).removeView(newItemView);
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                editTitle.setText("");
                //priority.setText("");
                editDesc.setText("");
                editShortDesc.setText("");
                dialog.dismiss();
                ((ViewGroup)newItemView.getParent()).removeView(newItemView);
            }
        });
    }
    private void askToDeleteDialog(final ViewItemHolder holder){
        final AlertDialog.Builder deleteDialog = new AlertDialog.Builder(MainActivity.this);
        if(deleteItemView == null){
            LayoutInflater layoutInflater = getLayoutInflater();
            deleteItemView = layoutInflater.inflate(R.layout.delete_item,null);
        }
        deleteDialog.setView(deleteItemView);
        deleteDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if(keyCode == KeyEvent.KEYCODE_BACK){
                    dialog.dismiss();
                    ((ViewGroup) deleteItemView.getParent()).removeView(deleteItemView);
                    viewItemAdapter.notifyDataSetChanged();
                    return true;
                }
                return false;
            }
        });
        deleteDialog.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                broadcastRemoveItem(holder);
                RadioButton ask = deleteItemView.findViewById(R.id.askRadioButton);
                if(ask.isChecked()){
                    askLater = false;
                }
                ((ViewGroup) deleteItemView.getParent()).removeView(deleteItemView);
            }
        });
        deleteDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                viewItemAdapter.notifyDataSetChanged();
                ((ViewGroup) deleteItemView.getParent()).removeView(deleteItemView);
            }
        });
        deleteDialog.show();
    }

    private void modifyDialog(final ViewItemHolder holder){
        final AlertDialog.Builder modifyDialog = new AlertDialog.Builder(MainActivity.this);
        if(modifyItemView == null) {
            LayoutInflater layoutInflater = getLayoutInflater();
            modifyItemView = layoutInflater.inflate(R.layout.modify_item, null);
        }
        TextView description = modifyItemView.findViewById(R.id.modDesc);
        TextView shortDescription = modifyItemView.findViewById(R.id.modShortDesc);
        Spinner priority = modifyItemView.findViewById(R.id.modPriority);

        description.setText(viewItemAdapter.getItem(holder.getAdapterPosition()).getDescription());
        shortDescription.setText(viewItemAdapter.getItem(holder.getAdapterPosition()).getShortDescription());

        switch (viewItemAdapter.getItem(holder.getAdapterPosition()).getStringPriority()){
            case "Low":
                priority.setSelection(0);
                break;
            case "Medium":
                priority.setSelection(1);
                break;
            case "High":
                priority.setSelection(2);
                break;
        }
        modifyDialog.setView(modifyItemView);
        modifyDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if(keyCode == KeyEvent.KEYCODE_BACK){
                    dialog.dismiss();
                    ((ViewGroup) modifyItemView.getParent()).removeView(modifyItemView);
                    return true;
                }
                return false;
            }
        });
        modifyDialog.setPositiveButton("Change", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String title = viewItemAdapter.getItem(holder.getAdapterPosition()).getTitle();
                Spinner priority = modifyItemView.findViewById(R.id.modPriority);
                EditText desc = modifyItemView.findViewById(R.id.modDesc);
                EditText shortDesc = modifyItemView.findViewById(R.id.modShortDesc);
                broadcastModifyItem(title,priority.getSelectedItem().toString(),desc.getText().toString(),shortDesc.getText().toString());

                ((ViewGroup) modifyItemView.getParent()).removeView(modifyItemView);
            }
        });
        modifyDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                viewItemAdapter.notifyDataSetChanged();
                ((ViewGroup) modifyItemView.getParent()).removeView(modifyItemView);
            }
        });
        modifyDialog.show();
    }

    private void broadcastRemoveItem(ViewItemHolder holder){
        MainActivity.this.sendBroadcast(new Intent(ControllerService.ACTION_REMOVE_ITEM).putExtra(ControllerService.EXTRA_TITLE,viewItemAdapter.getItem(holder.getAdapterPosition()).getTitle().trim()));
    }

    private void broadcastModifyItem(String title, String priority,String description, String shortDescription){

        MainActivity.this.sendBroadcast(new Intent(ControllerService.ACTION_MODIFY_ITEM)
                .putExtra(ControllerService.EXTRA_TITLE,title.trim())
                .putExtra(ControllerService.EXTRA_PRIORITY,priority.trim())
                .putExtra(ControllerService.EXTRA_DESCRIPTION,description.trim())
                .putExtra(ControllerService.EXTRA_SHORT_DESCRIPTION,shortDescription.trim()));
    }
    private void broadcastAddItem(EditText editTitle,Spinner editPriority, EditText editDesc, EditText editShortDesc){
        getApplicationContext().sendBroadcast(new Intent(ControllerService.ACTION_ADD_ITEM)
                .putExtra(ControllerService.EXTRA_TITLE,editTitle.getText().toString().trim())
                .putExtra(ControllerService.EXTRA_PRIORITY,editPriority.getSelectedItem().toString().trim())
                .putExtra(ControllerService.EXTRA_DESCRIPTION,editDesc.getText().toString().trim())
                .putExtra(ControllerService.EXTRA_SHORT_DESCRIPTION,editShortDesc.getText().toString().trim()));
    }

    //Object for RecyclerView.ViewHolder
    private class ViewItem {
        private String title;
        private String priority;
        private String description;
        private String shortDescription;
        private boolean showingFullDesc = false;

        public ViewItem(String title, String priority, String description, String shortDescription){
            this.title = title;
            this.priority = priority;
            this.description = description;
            this.shortDescription = shortDescription;
        }

        public String getTitle() {
            return title;
        }

        public Bitmap getBitmapPriority() {

            switch (priority){
                case "Low":
                    return Bitmap.createBitmap(BitmapFactory.decodeResource(getResources(),R.drawable.low_priority));
                case "Medium":
                    return Bitmap.createBitmap(BitmapFactory.decodeResource(getResources(),R.drawable.medium_priority));
                case "High":
                    return Bitmap.createBitmap(BitmapFactory.decodeResource(getResources(),R.drawable.high_priority));
                default:
                    return Bitmap.createBitmap(BitmapFactory.decodeResource(getResources(),R.drawable.low_priority));
            }

        }
        public String getStringPriority(){
            return this.priority;
        }

        public String getDescription() {
            return description;
        }

        public String getShortDescription() {
            return shortDescription;
        }

        public boolean getShowingFullDesc(){
            return showingFullDesc;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setShortDescription(String shortDescription) {
            this.shortDescription = shortDescription;
        }

        public void setShowingFullDesc(boolean showFullDesc) {
            this.showingFullDesc = showFullDesc;
        }

    }

    private class ViewItemHolder extends RecyclerView.ViewHolder {

        ConstraintLayout constraintLayout;
        TextView title;
        TextView description;
        TextView short_description;
        ImageView priority;

        public ViewItemHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.todoTitle);
            description = itemView.findViewById(R.id.todoDesc);
            short_description = itemView.findViewById(R.id.todoShortDesc);
            priority = itemView.findViewById(R.id.todoPriority);
            constraintLayout = itemView.findViewById(R.id.rowConstraintLayout);
        }
    }

    private class ViewItemAdapter extends RecyclerView.Adapter<ViewItemHolder>{

        private ArrayList<ViewItem> items;
        private int resource;
        private Context context;

        public ViewItemAdapter(Context context, ArrayList<ViewItem> items, int resource){
            this.context = context;
            this.items = items;
            this.resource = resource;
        }


        public void addItem(ViewItem item){

            items.add(item);
            notifyItemInserted(items.size() - 1);
        }

        public void addItemAt(int position,ViewItem item){
            items.add(position,item);
            notifyItemInserted(position);
        }

        public void deleteItem(String title){
            for(ViewItem t : items){
                if(t.getTitle().equals(title)){
                    int position = items.indexOf(t);
                    items.remove(t);
                    notifyItemRemoved(position);
                    break;
                }
            }

        }

        public ViewItem getItem(int position){
            return items.get(position);
        }

        public void modifyItem(String title, String priority, String description, String shortDescription){
            for(ViewItem t : items){
                if(t.getTitle().equals(title)){
                    Log.d(TAG, "Modified: "+ title);
                    int position = items.indexOf(t);
                    t.setDescription(description);
                    t.setPriority(priority);
                    t.setShortDescription(shortDescription);
                    notifyItemChanged(position);
                }
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public ViewItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(resource,parent,false);
            return new ViewItemHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewItemHolder holder, final int position) {

            final ViewItem temp = items.get(position);
            boolean show = temp.getShowingFullDesc();
            holder.title.setText(temp.getTitle());
            holder.short_description.setText(temp.getShortDescription());
            holder.description.setText(temp.getDescription());
            holder.priority.setImageBitmap(temp.getBitmapPriority());

            if(show) {
                holder.description.setVisibility(View.VISIBLE);
                holder.short_description.setVisibility(View.GONE);
            } else {
                holder.short_description.setVisibility(View.VISIBLE);
                holder.description.setVisibility(View.GONE);
            }

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(items.get(holder.getAdapterPosition()).getShowingFullDesc()){
                        holder.description.setVisibility(View.GONE);
                        holder.short_description.setVisibility(View.VISIBLE);
                        items.get(holder.getAdapterPosition()).setShowingFullDesc(false);
                    } else {
                        holder.short_description.setVisibility(View.GONE);
                        holder.description.setVisibility(View.VISIBLE);
                        items.get(holder.getAdapterPosition()).setShowingFullDesc(true);
                    }
                }
            });

        }

    }

    private void initItemTouchHelper(){
        itemSwipe = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, int direction) {
                Log.d(TAG,"Swiped");

                final ViewItemHolder holder = (ViewItemHolder) viewHolder;
                ConstraintSet set = new ConstraintSet();
                set.clone(holder.constraintLayout);
                if(direction == ItemTouchHelper.LEFT){
                    Log.d(TAG,"Swiped Left");
                    if(askLater){
                        askToDeleteDialog(holder);
                    }
                    else {
                        broadcastRemoveItem(holder);
                    }
                }
                else if(direction == ItemTouchHelper.RIGHT){
                    Log.d(TAG,"Swiped Right");
                    modifyDialog(holder);
                }
            }

            @Override
            public void onChildDrawOver(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {

                super.onChildDrawOver(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        };
    }

    private class ToDoBroadcastReceiver extends ViewBroadcastReceiver{

        @Override
        protected void gotSentItem(Intent intent) {
            String title = intent.getStringExtra(ControllerService.EXTRA_TITLE);
            String priority = intent.getStringExtra(ControllerService.EXTRA_PRIORITY);
            String desc = intent.getStringExtra(ControllerService.EXTRA_DESCRIPTION);
            String short_desc = intent.getStringExtra(ControllerService.EXTRA_SHORT_DESCRIPTION);

            ViewItem viewItem = new ViewItem(title,priority,desc,short_desc);

            viewItemAdapter.addItem(viewItem);
        }

        @Override
        protected void gotSentDeleteItem(Intent intent) {
            String title = intent.getStringExtra(ControllerService.EXTRA_TITLE);
            viewItemAdapter.deleteItem(title);
        }

        @Override
        protected void getSentModifyItem(Intent intent) {
            String title = intent.getStringExtra(ControllerService.EXTRA_TITLE);
            String priority = intent.getStringExtra(ControllerService.EXTRA_PRIORITY);
            String desc = intent.getStringExtra(ControllerService.EXTRA_DESCRIPTION);
            String short_desc = intent.getStringExtra(ControllerService.EXTRA_SHORT_DESCRIPTION);
            viewItemAdapter.modifyItem(title,priority,desc,short_desc);
        }

        //Not used for this view
        @Override
        protected void gotSentPriority(Intent intent) {}
        @Override
        protected void gotSentDescription(Intent intent) {}
        @Override
        protected void gotSentShortDescription(Intent intent) {}
    }

}
