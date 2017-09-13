package com.ingenic.humandec;

import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;
import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.String;

import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import android.util.Log; //

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;

import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.stub.MetadataUtils;

import tensorflow.serving.Model.ModelSpec;
import tensorflow.serving.Predict;
import tensorflow.serving.Predict.PredictRequest;
import tensorflow.serving.Predict.PredictResponse;
import tensorflow.serving.PredictionServiceGrpc;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        String CustomerID = "ingenic_test";
        String path="null";
        long size = 0;
        int readsize = -1;
        int postsize = -1;
        boolean requeststate = false;

        String result="result:\n";
        path= Environment.getExternalStorageDirectory().getPath()+"/test2.jpeg";
        String pem_path = Environment.getExternalStorageDirectory().getPath()+"/ca.crt";
        File file = new File(path);
        File Sslfile = new File(pem_path);

        ManagedChannel mChannel = null;
        try {
            mChannel= OkHttpChannelBuilder.forAddress("clouds.ingenic.com",32296)
                    .overrideAuthority("clouds.ingenic.com")
                    .sslSocketFactory(getSslSocketFactory( new FileInputStream(Sslfile))).build();
        } catch (Exception e) {
            e.printStackTrace();
        }

        PredictionServiceGrpc.PredictionServiceBlockingStub stub;
        int counter = 0;
        while(counter < 2) {
            counter++;
            if (file.exists()) {
                size = file.length();
                try {
                    FileInputStream fi = new FileInputStream(file);
                    ByteArrayOutputStream bytearray = new ByteArrayOutputStream();
                    ByteArrayOutputStream codearray = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    byte[] codebuf = new byte[192];
                    int codelen = -1;
                    codelen = fi.read(codebuf);
                    if(codelen!=-1) {
                        codearray.write(codebuf, 0, codelen);
                    }
                    ByteString verifycode = ByteString.copyFrom(codearray.toByteArray(), 0, codearray.size());

                    int len = -1;
                    len = fi.read(buffer);
                    while (len != -1) {
                        bytearray.write(buffer, 0, len);
                        len = fi.read(buffer);
                    }
                    fi.close();
                    buffer = null;
                    codebuf = null;
                    readsize = bytearray.size();
                    String key = "images";
                    ByteString img = ByteString.copyFrom(bytearray.toByteArray(), 0, readsize);
                    bytearray.close();
                    codearray.close();
                    postsize = img.size();
                    PredictRequest request = PredictRequest.newBuilder().setModelSpec(ModelSpec.newBuilder().setName("humandec")
                            .setSignatureName("predict_images").build())
                            .addSizes(postsize)
                            .addStringVal(verifycode)
                            .putInputs(key, img).build();
                    PredictResponse response = PredictResponse.newBuilder().build();
                    try {
                        if (mChannel.isShutdown() || mChannel.isTerminated()) {
                            try {
                                mChannel = OkHttpChannelBuilder.forAddress("clouds.ingenic.com", 32296)
                                        .overrideAuthority("clouds.ingenic.com")
                                        .sslSocketFactory(getSslSocketFactory(new FileInputStream(Sslfile))).build();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        //create a custom header
                        Metadata header=new Metadata();
                        Metadata.Key<String> kid = Metadata.Key.of("id", Metadata.ASCII_STRING_MARSHALLER);
                        header.put(kid, CustomerID);

                        // create client stub
                        stub = PredictionServiceGrpc.newBlockingStub(mChannel).withDeadlineAfter(20000, TimeUnit.MILLISECONDS);
                        stub = MetadataUtils.attachHeaders(stub, header);
                        response = stub.predict(request);
                    /*get result*/
                        Map<String, Predict.ResponseContext> outputs = response.getOutputsMap();
                        Predict.ResponseContext output = outputs.get("scores");
                        List<Float> list = output.getFloatValList();
                        List<Float> sublist = list.subList(1, list.size());
                        result += "scores: ";
                        result = result.concat(sublist.toString());
                        output = outputs.get("bboxes");
                        list = output.getFloatValList();
                        sublist = list.subList(4, list.size());
                        result += "\nbboxes: ";

                        result += sublist.toString();
                        TextView mResult;
                        mResult = (TextView)findViewById(R.id.response);
                        mResult.setText(result /*String.valueOf(count)*/);
                    } catch (Exception grpce) {
                        grpce.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    private static SSLSocketFactory getSslSocketFactory(InputStream testCa)
            throws Exception {
        if (testCa == null) {
            return (SSLSocketFactory) SSLSocketFactory.getDefault();
        }
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, getTrustManagers(testCa), null);
        return context.getSocketFactory();
    }

    private static TrustManager[] getTrustManagers(InputStream testCa) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf.generateCertificate(testCa);
        X500Principal principal = cert.getSubjectX500Principal();
        ks.setCertificateEntry(principal.getName("RFC2253"), cert);
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(ks);
        return trustManagerFactory.getTrustManagers();
    }


}
