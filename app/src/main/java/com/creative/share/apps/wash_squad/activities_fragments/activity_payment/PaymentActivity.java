package com.creative.share.apps.wash_squad.activities_fragments.activity_payment;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.creative.share.apps.wash_squad.R;
import com.creative.share.apps.wash_squad.adapters.AdditionalAdapter;
import com.creative.share.apps.wash_squad.databinding.ActivityPaymentBinding;
import com.creative.share.apps.wash_squad.interfaces.Listeners;
import com.creative.share.apps.wash_squad.language.LanguageHelper;
import com.creative.share.apps.wash_squad.models.CouponModel;
import com.creative.share.apps.wash_squad.models.ItemToUpload;
import com.creative.share.apps.wash_squad.models.Order_Data_Model;
import com.creative.share.apps.wash_squad.models.UserModel;
import com.creative.share.apps.wash_squad.preferences.Preferences;
import com.creative.share.apps.wash_squad.remote.Api;
import com.creative.share.apps.wash_squad.share.Common;
import com.creative.share.apps.wash_squad.singleton.SingleTon;
import com.creative.share.apps.wash_squad.tags.Tags;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import io.paperdb.Paper;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PaymentActivity extends AppCompatActivity implements Listeners.BackListener {
    private ActivityPaymentBinding binding;
    private String lang;
    private ItemToUpload itemToUpload;
    private SingleTon singleTon;
    private LinearLayoutManager manager;
    private AdditionalAdapter adapter;
    private double total=0.0;
    private double coupon_value = 0;
    private CouponModel couponModel = null;
    private Preferences preferences;
    private UserModel userModel;


    @Override
    protected void attachBaseContext(Context newBase) {
        Paper.init(newBase);
        super.attachBaseContext(LanguageHelper.updateResources(newBase, Paper.book().read("lang", Locale.getDefault().getLanguage())));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_payment);
        getDataFromIntent();
        initView();

    }

    private void getDataFromIntent() {
        Intent intent = getIntent();
        if (intent!=null)
        {
            itemToUpload = (ItemToUpload) intent.getSerializableExtra("item");
        }
    }


    private void initView() {
        preferences = Preferences.newInstance();
        userModel = preferences.getUserData(this);
        singleTon = SingleTon.newInstance();
        binding.setItemModel(itemToUpload);

        Paper.init(this);
        lang = Paper.book().read("lang", Locale.getDefault().getLanguage());
        binding.setLang(lang);
        binding.setBackListener(this);
        binding.progBar.getIndeterminateDrawable().setColorFilter(ContextCompat.getColor(this, R.color.colorPrimary), PorterDuff.Mode.SRC_IN);

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE dd/MMM",Locale.ENGLISH);
       // String m_date = dateFormat.format(new Date(itemToUpload.getOrder_date()*1000));
        String m_date=itemToUpload.getOrder_date();
        binding.tvDate.setText(String.format("%s %s %s",m_date,itemToUpload.getTime(),itemToUpload.getTime_type()));


        if (itemToUpload.getSub_services().size()>0)
        {
            manager = new LinearLayoutManager(this);
            adapter = new AdditionalAdapter(itemToUpload.getSub_services(),this);
            binding.recView.setLayoutManager(manager);
            binding.recView.setAdapter(adapter);


            binding.tvNoAdditionalServices.setVisibility(View.GONE);
        }else
            {
                binding.tvNoAdditionalServices.setVisibility(View.VISIBLE);

            }
        binding.rb1.setOnClickListener(view ->
        {
            itemToUpload.setPayment_method(1);
            binding.setItemModel(itemToUpload);
            binding.tvPayment.setText(R.string.cache);
        });
        binding.rb2.setOnClickListener(view ->
        {
            itemToUpload.setPayment_method(2);
            binding.setItemModel(itemToUpload);
            binding.tvPayment.setText(R.string.mada);

        });

        binding.btnSend.setOnClickListener(view -> {
            if (itemToUpload.isDataValidStep2(this))
            {
                if (couponModel==null)
                {
                    itemToUpload.setCoupon_serial(null);
                }else
                    {
                        itemToUpload.setCoupon_serial(couponModel.getCoupon_serial());
                    }
                uploadOrder(itemToUpload);
            }
        });

/*
        binding.btnOther.setOnClickListener(view -> {
            if (itemToUpload.isDataValidStep2(this))
            {

                if (couponModel==null)
                {
                    itemToUpload.setCoupon_serial(null);
                }else
                {
                    itemToUpload.setCoupon_serial(couponModel.getCoupon_serial());
                }


                singleTon.addItem(itemToUpload);
                Intent intent = getIntent();
                if (intent!=null)
                {
                    setResult(RESULT_OK,intent);
                }
                finish();
            }
        });
*/

        binding.btnDiscount.setOnClickListener(view -> {
            String coupon = binding.edtCoupon.getText().toString().trim();

            if (!TextUtils.isEmpty(coupon))
            {
                binding.edtCoupon.setError(null);
                Common.CloseKeyBoard(this,binding.edtCoupon);
                getCouponValue(coupon);

            }else
                {
                    binding.edtCoupon.setError(getString(R.string.field_req));
                }
        });

        updateTotalPrice(coupon_value);

    }

    private void updateTotalPrice(double coupon_value)
    {
        total = 0;

        /*if (itemToUpload.getSub_services().size()>0)
        {
            total +=itemToUpload.getService_price();
            total += getAdditionalServicePrice(itemToUpload.getSub_services());

            total = total -((total*coupon_value)/100);
        }else
            {
                total +=(itemToUpload.getService_price())-((itemToUpload.getService_price()*coupon_value)/100);

            }*/

        total = itemToUpload.getTotal_price()-((itemToUpload.getTotal_price()*coupon_value)/100);


        itemToUpload.setTotal_price(total);
    }

    private void getCouponValue(String coupon) {



        binding.progBar.setVisibility(View.VISIBLE);
        binding.iconChecked.setVisibility(View.GONE);
        Api.getService(Tags.base_url)
                .getCoupon(userModel.getId(),coupon)
                .enqueue(new Callback<CouponModel>() {
                    @Override
                    public void onResponse(Call<CouponModel> call, Response<CouponModel> response) {
                        binding.progBar.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null) {

                            couponModel = response.body();
                            updateTotalPrice(couponModel.getRatio()/100.0);
                            binding.tvCoupon.setText(String.format(Locale.ENGLISH,"%s %s",String.valueOf(couponModel.getRatio()/100.0),getString(R.string.discount)));
                            binding.iconChecked.setVisibility(View.VISIBLE);
                            Common.CreateDialogAlert(PaymentActivity.this,getString(R.string.cong)+" "+(couponModel.getRatio()/100)+" "+getString(R.string.disc));

                        } else {

                            try {

                                Log.e("error", response.code() + "_" + response.errorBody().string());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            if (response.code() == 422) {
                                Common.CreateDialogAlert(PaymentActivity.this,getString(R.string.inv_coupon));
                            } else if (response.code() == 500) {
                                Toast.makeText(PaymentActivity.this, "Server Error", Toast.LENGTH_SHORT).show();

                            } else {
                                Toast.makeText(PaymentActivity.this, getString(R.string.failed), Toast.LENGTH_SHORT).show();


                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<CouponModel> call, Throwable t) {
                        try {
                            binding.progBar.setVisibility(View.GONE);
                            if (t.getMessage() != null) {

                                Log.e("error", t.getMessage());
                                if (t.getMessage().toLowerCase().contains("failed to connect") || t.getMessage().toLowerCase().contains("unable to resolve host")) {
                                    Toast.makeText(PaymentActivity.this, R.string.something, Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(PaymentActivity.this, t.getMessage(), Toast.LENGTH_SHORT).show();
                                }
                            }

                        } catch (Exception e) {
                        }
                    }
                });
    }

    private double getAdditionalServicePrice(List<ItemToUpload.SubServiceModel> sub_services) {
        double total = 0.0;
        for (ItemToUpload.SubServiceModel subServiceModel:sub_services)
        {
            total +=subServiceModel.getPrice();
        }
        return total;

    }


    private void uploadOrder(ItemToUpload itemToUpload)
    {

        ProgressDialog dialog = Common.createProgressDialog(this,getString(R.string.wait));
        dialog.setCancelable(false);
        dialog.show();
        try {



            Api.getService(Tags.base_url)
                    .addOrder(itemToUpload)
                    .enqueue(new Callback<Order_Data_Model.OrderModel>() {
                        @Override
                        public void onResponse(Call<Order_Data_Model.OrderModel> call, Response<Order_Data_Model.OrderModel> response) {
                            dialog.dismiss();
                            if (response.isSuccessful()&&response.body()!=null)
                            {
                                Toast.makeText(PaymentActivity.this, getString(R.string.suc), Toast.LENGTH_LONG).show();
                                Intent intent = getIntent();
                                if (intent!=null)
                                {
                                    setResult(RESULT_OK,intent);
                                }
                                finish();
                            }else
                            {
                                try {

                                    Log.e("error",response.code()+"_"+response.errorBody().string());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                if (response.code() == 422) {
                                    Toast.makeText(PaymentActivity.this, getString(R.string.failed), Toast.LENGTH_SHORT).show();

                                }else if (response.code() == 402) {
                                    Toast.makeText(PaymentActivity.this, R.string.num_exceed, Toast.LENGTH_SHORT).show();

                                }else if (response.code() == 500) {
                                    Toast.makeText(PaymentActivity.this, "Server Error", Toast.LENGTH_SHORT).show();

                                }else
                                {
                                    Toast.makeText(PaymentActivity.this, getString(R.string.failed), Toast.LENGTH_SHORT).show();


                                }
                            }
                        }

                        @Override
                        public void onFailure(Call<Order_Data_Model.OrderModel> call, Throwable t) {
                            try {
                                dialog.dismiss();
                                if (t.getMessage()!=null)
                                {
                                    Log.e("error",t.getMessage());
                                    if (t.getMessage().toLowerCase().contains("failed to connect")||t.getMessage().toLowerCase().contains("unable to resolve host"))
                                    {
                                        Toast.makeText(PaymentActivity.this,R.string.something, Toast.LENGTH_SHORT).show();
                                    }else
                                    {
                                        Toast.makeText(PaymentActivity.this,t.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                }

                            }catch (Exception e)
                            {
                                Log.e("ec",e.getMessage()+"_");
                            }
                        }
                    });
        }catch (Exception e){
            Log.e("edddc",e.getMessage()+"_");

            dialog.dismiss();

        }

    }

    @Override
    public void back() {
        finish();
    }

}
