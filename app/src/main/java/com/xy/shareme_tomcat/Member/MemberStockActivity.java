package com.xy.shareme_tomcat.Member;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.xy.shareme_tomcat.Product.ProductDetailActivity;
import com.xy.shareme_tomcat.R;
import com.xy.shareme_tomcat.adapter.StockListAdapter;
import com.xy.shareme_tomcat.data.Book;
import com.xy.shareme_tomcat.data.ImageObj;
import com.xy.shareme_tomcat.network_helper.MyOkHttp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import static com.xy.shareme_tomcat.data.DataHelper.KEY_PHOTO1;
import static com.xy.shareme_tomcat.data.DataHelper.KEY_PRICE;
import static com.xy.shareme_tomcat.data.DataHelper.KEY_PRODUCT_ID;
import static com.xy.shareme_tomcat.data.DataHelper.KEY_STATUS;
import static com.xy.shareme_tomcat.data.DataHelper.KEY_STOCK;
import static com.xy.shareme_tomcat.data.DataHelper.KEY_TITLE;
import static com.xy.shareme_tomcat.data.DataHelper.KEY_USER_ID;
import static com.xy.shareme_tomcat.data.DataHelper.getSimpleAdapter;
import static com.xy.shareme_tomcat.data.DataHelper.loginUserId;
import static com.xy.shareme_tomcat.data.DataHelper.showFoundStatus;

public class MemberStockActivity extends AppCompatActivity {
    private Context context;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ListView lstProduct;
    private ProgressBar prgBar;

    private ArrayList<ImageObj> books;
    private StockListAdapter adapter;

    private Dialog dialog;
    private String bookId, bookTitle;

    private MyOkHttp conLoadStock, conDropProduct;
    private boolean isShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_member_stock);
        context = this;
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle("商品管理");
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        swipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setColorSchemeResources(android.R.color.holo_blue_light);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                adapter.destroy(false);
                adapter = null;
                loadData(false);
            }
        });

        lstProduct = (ListView) findViewById(R.id.lstProduct);
        lstProduct.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                Book book = (Book) adapter.getItem(position);
                bookId = book.getId();
                bookTitle = book.getTitle();
                TextView textView = (TextView) dialog.findViewById(R.id.txtBookTitle);
                textView.setText(bookTitle);
                dialog.show();
            }
        });

        prgBar = (ProgressBar) findViewById(R.id.prgBar);
        prepareDialog();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isShown)
            loadData(true);
    }

    private void prepareDialog() {
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dlg_stock_mgt);
        dialog.setCancelable(true);

        String[] textGroup = {"查看", "編輯", "下架"};
        int[] iconGroup = {
                R.drawable.icon_check,
                R.drawable.icon_edit,
                R.drawable.icon_delete
        };

        ListView listView = (ListView) dialog.findViewById(R.id.lstStockMgt);
        listView.setAdapter(getSimpleAdapter(
                context,
                R.layout.lst_text_with_icon_black,
                R.id.imgIcon,
                R.id.txtTitle,
                iconGroup,
                textGroup
        ));

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent it = null;
                Bundle bundle = new Bundle();
                switch (position) {
                    case 0:
                        it = new Intent(context, ProductDetailActivity.class);
                        bundle.putString(KEY_PRODUCT_ID, bookId);
                        bundle.putString(KEY_TITLE, bookTitle);
                        it.putExtras(bundle);
                        startActivity(it);
                        break;
                    case 1:
                        it = new Intent(context, ProductEditActivity.class);
                        bundle.putString(KEY_PRODUCT_ID, bookId);
                        it.putExtras(bundle);
                        startActivity(it);
                        break;
                    case 2:
                        AlertDialog.Builder msgbox = new AlertDialog.Builder(context);
                        msgbox.setTitle("下架商品")
                                .setMessage(getString(R.string.hint_drop_product, bookTitle))
                                .setPositiveButton("確定", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dropProduct(bookId);
                                    }
                                })
                                .setNegativeButton("取消", null)
                                .show();
                        break;
                    case 3:
                        break;
                }
                dialog.dismiss();
            }
        });
    }

    private void loadData(boolean showPrgBar) {
        isShown = false;
        swipeRefreshLayout.setEnabled(false);
        if (showPrgBar)
            prgBar.setVisibility(View.VISIBLE);

        books = new ArrayList<>();
        conLoadStock = new MyOkHttp(MemberStockActivity.this, new MyOkHttp.TaskListener() {
            @Override
            public void onFinished(final String result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null) {
                            Toast.makeText(context, "連線失敗", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ImageView imageView = (ImageView) findViewById(R.id.imgNotFound);
                        TextView textView = (TextView) findViewById(R.id.txtNotFound);
                        try {
                            JSONObject resObj = new JSONObject(result);
                            if (resObj.getBoolean(KEY_STATUS)) {
                                JSONArray ary = resObj.getJSONArray(KEY_STOCK);
                                for (int i=0; i<ary.length(); i++) {
                                    JSONObject obj = ary.getJSONObject(i);
                                    books.add(new Book(
                                            obj.getString(KEY_PRODUCT_ID),
                                            obj.getString(KEY_PHOTO1),
                                            obj.getString(KEY_TITLE),
                                            obj.getString(KEY_PRICE)
                                    ));
                                }
                                showFoundStatus(books, imageView, textView, "");
                                showData();
                                prgBar.setVisibility(View.GONE);
                            }else {
                                prgBar.setVisibility(View.GONE);
                                showFoundStatus(books, imageView, textView, "沒有上架的商品");
                            }
                        }catch (JSONException e) {
                            prgBar.setVisibility(View.GONE);
                            showFoundStatus(books, imageView, textView, "處理JSON發生錯誤");
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
        try {
            JSONObject reqObj = new JSONObject();
            reqObj.put(KEY_USER_ID, loginUserId);
            conLoadStock.execute(getString(R.string.link_show_stock), reqObj.toString());
        }catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showData() {
        adapter = new StockListAdapter(getResources(), context, books, R.layout.lst_stock, 10);
        adapter.setBackgroundColor(R.color.lst_stock);
        lstProduct.setAdapter(adapter);

        books = null;
        lstProduct.setVisibility(View.VISIBLE);
        swipeRefreshLayout.setEnabled(true);
        swipeRefreshLayout.setRefreshing(false);

        isShown = true;
    }

    private void dropProduct(String bookId) {
        lstProduct.setVisibility(View.GONE);
        prgBar.setVisibility(View.VISIBLE);
        conDropProduct = new MyOkHttp(MemberStockActivity.this, new MyOkHttp.TaskListener() {
            @Override
            public void onFinished(final String result) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null) {
                            Toast.makeText(context, "連線失敗", Toast.LENGTH_SHORT).show();
                            prgBar.setVisibility(View.GONE);
                            return;
                        }
                        try {
                            JSONObject resObj = new JSONObject(result);
                            if (resObj.getBoolean(KEY_STATUS)) {
                                Toast.makeText(context, "商品成功下架", Toast.LENGTH_SHORT).show();
                                adapter.destroy(true);
                                adapter = null;
                                loadData(true);
                            }else {
                                prgBar.setVisibility(View.GONE);
                                Toast.makeText(context, "伺服器發生例外", Toast.LENGTH_SHORT).show();
                            }
                        }catch (JSONException e) {
                            prgBar.setVisibility(View.GONE);
                            e.printStackTrace();
                        }
                    }
                });
            }
        });
        try {
            JSONObject reqObj = new JSONObject();
            reqObj.put(KEY_PRODUCT_ID, bookId);
            conDropProduct.execute(getString(R.string.link_drop_product), reqObj.toString());
        }catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        cancelConnection();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (adapter != null) {
            adapter.destroy(true);
            adapter = null;
        }
        System.gc();
        super.onDestroy();
    }

    private void cancelConnection() {
        try {
            conLoadStock.cancel();
        }catch (NullPointerException e) {}
        try {
            conDropProduct.cancel();
        }catch (NullPointerException e) {}
    }
}
