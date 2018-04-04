package com.navido.loginverify;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import es.dmoral.prefs.Prefs;

public class LoginActivity extends AppCompatActivity {

    private static String TAG = "LoginActivity";

    @BindView(R.id.phonenumber_edit)
    EditText phonenumberEdit;

    @BindView(R.id.code_edit)
    EditText codeEdit;

    @BindView(R.id.firstname_edit)
    EditText firstnameEdit;

    @BindView(R.id.lastname_edit)
    EditText lastnameEdit;

    @BindView(R.id.send_button)
    Button sendButton;

    @BindView(R.id.verify_button)
    Button verifyButton;

    @BindView(R.id.code_layout)
    LinearLayout codeLayout;

    PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;
    private  FirebaseUser firebaseUser;

    private String phoneNumber;
    private String verificationId;
    private static String uniqueIdentifier = null;
    private static final String UNIQUE_ID = "UNIQUE_ID";

    private FirebaseFirestore firestoreDB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        ButterKnife.bind(this);
        firestoreDB = FirebaseFirestore.getInstance();
        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(PhoneAuthCredential phoneAuthCredential) {

            }

            @Override
            public void onVerificationFailed(FirebaseException e) {

                Log.e(TAG ,e.getLocalizedMessage());
            }

            @Override
            public void onCodeSent(String verificationId, PhoneAuthProvider.ForceResendingToken var2) {

                Log.d(TAG ,verificationId);
                LoginActivity.this.verificationId = verificationId;
                verifyMode();
                addVerificationDataToFirestore(phoneNumber, verificationId);
            }

            @Override
            public void onCodeAutoRetrievalTimeOut(String var1) {

            }
        };

        getInstallationIdentifier();
        //getVerificationDataFromFirestoreAndVerify(null);

        phonenumberEdit.setText(Prefs.with(LoginActivity.this).read("phone_number", ""));
        if (FirebaseAuth.getInstance().getCurrentUser() != null){

            Intent intent = new Intent(this ,HomeActivity.class);
            startActivity(intent);
        }
    }

    public void onSMS(View view)
    {
        phoneNumber = phonenumberEdit.getText().toString();
        if (!validatePhoneNumber(phoneNumber)) {
            phonenumberEdit.setError("Invalid phone number.");
            return;
        }
        verifyPhoneNumber(phoneNumber);

    }

    public void onVerify(View view)
    {
        final String phone_code = codeEdit.getText().toString();
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, phone_code);
        signInWithPhoneAuthCredential(credential);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "code verified signIn successful");
                            firebaseUser = task.getResult().getUser();

                            // save phone number
                            Prefs.with(LoginActivity.this).write("phone_number", phoneNumber);
                            Intent intent = new Intent(LoginActivity.this ,HomeActivity.class);
                            startActivity(intent);
                            initMode();

                        } else {
                            Log.w(TAG, "code verification failed", task.getException());
                            if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                                codeEdit.setError("Invalid code.");
                            }
                        }
                    }
                });
    }

    private boolean validatePhoneNumber(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            phonenumberEdit.setError("Invalid phone number.");
            return false;
        }
        return true;
    }

    private void initMode()
    {
        phonenumberEdit.setEnabled(true);
        codeLayout.setVisibility(View.GONE);
        sendButton.setVisibility(View.VISIBLE);
        verifyButton.setVisibility(View.GONE);
    }

    private void verifyMode()
    {
        phonenumberEdit.setEnabled(false);
        firstnameEdit.setEnabled(false);
        lastnameEdit.setEnabled(false);
        codeLayout.setVisibility(View.VISIBLE);
        sendButton.setVisibility(View.GONE);
        verifyButton.setVisibility(View.VISIBLE);
    }

    private void addVerificationDataToFirestore(String phone, String verificationId) {
        Map verifyMap = new HashMap();
        verifyMap.put("phone", phone);
        verifyMap.put("verificationId", verificationId);
        verifyMap.put("firstname", firstnameEdit.getText().toString());
        verifyMap.put("lastname", lastnameEdit.getText().toString());
        verifyMap.put("timestamp",System.currentTimeMillis());

        firestoreDB.collection("phoneAuth").document(uniqueIdentifier)
                .set(verifyMap)
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        Log.d(TAG, "phone auth info added to db ");

                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error adding phone auth info", e);
                    }
                });
    }

    private void getVerificationDataFromFirestoreAndVerify(final String code) {

        firestoreDB.collection("phoneAuth").document(uniqueIdentifier)
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot ds = task.getResult();
                            if(ds.exists()){

                                if(code != null){
                                    createCredentialSignIn(ds.getString("verificationId"), code);
                                }else{
                                    verifyPhoneNumber(ds.getString("phone"));
                                }
                            }else{
                                Log.d(TAG, "Code hasn't been sent yet");
                            }

                        } else {
                            Log.d(TAG, "Error getting document: ", task.getException());
                        }
                    }
                });
    }

    public void getInstallationIdentifier() {

//        uniqueIdentifier = Prefs.with(this).read(UNIQUE_ID ,null);
//        if (uniqueIdentifier == null) {
//
//            uniqueIdentifier = UUID.randomUUID().toString();
//            Prefs.with(this).write(UNIQUE_ID, uniqueIdentifier);
//        }
        uniqueIdentifier = UUID.randomUUID().toString();
    }

    private void createCredentialSignIn(String verificationId, String verifyCode) {
        PhoneAuthCredential credential = PhoneAuthProvider.
                getCredential(verificationId, verifyCode);
        signInWithPhoneAuthCredential(credential);
    }

    private void verifyPhoneNumber(String phno){

        PhoneAuthProvider.getInstance().verifyPhoneNumber(phno, 60, TimeUnit.SECONDS, this, mCallbacks);

    }
}
