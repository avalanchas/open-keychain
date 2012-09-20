/*
 * Copyright (C) 2011 Senecaso
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.thialfihar.android.apg.ui;

import java.util.Iterator;

import org.spongycastle.openpgp.PGPPublicKeyRing;
import org.spongycastle.openpgp.PGPSignature;
import org.thialfihar.android.apg.Constants;
import org.thialfihar.android.apg.Id;
import org.thialfihar.android.apg.R;
import org.thialfihar.android.apg.helper.PGPMain;
import org.thialfihar.android.apg.helper.Preferences;
import org.thialfihar.android.apg.provider.ProviderHelper;
import org.thialfihar.android.apg.service.ApgService;
import org.thialfihar.android.apg.service.ApgServiceHandler;
import org.thialfihar.android.apg.service.PassphraseCacheService;
import org.thialfihar.android.apg.ui.dialog.PassphraseDialogFragment;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Messenger;

import org.thialfihar.android.apg.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;
import android.widget.Toast;

/**
 * gpg --sign-key
 * 
 * signs the specified public key with the specified secret master key
 */
public class SignKeyActivity extends SherlockFragmentActivity {

    public static final String EXTRA_KEY_ID = "keyId";

    private long mPubKeyId = 0;
    private long mMasterKeyId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // check we havent already signed it
        setContentView(R.layout.sign_key_layout);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setHomeButtonEnabled(false);

        final Spinner keyServer = (Spinner) findViewById(R.id.keyServer);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, Preferences.getPreferences(this)
                        .getKeyServers());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        keyServer.setAdapter(adapter);

        final CheckBox sendKey = (CheckBox) findViewById(R.id.sendKey);
        if (!sendKey.isChecked()) {
            keyServer.setEnabled(false);
        } else {
            keyServer.setEnabled(true);
        }

        sendKey.setOnCheckedChangeListener(new OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!isChecked) {
                    keyServer.setEnabled(false);
                } else {
                    keyServer.setEnabled(true);
                }
            }
        });

        Button sign = (Button) findViewById(R.id.sign);
        sign.setEnabled(false); // disabled until the user selects a key to sign with
        sign.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mPubKeyId != 0) {
                    initiateSigning();
                }
            }
        });

        mPubKeyId = getIntent().getLongExtra(EXTRA_KEY_ID, 0);
        if (mPubKeyId == 0) {
            finish(); // nothing to do if we dont know what key to sign
        } else {
            // kick off the SecretKey selection activity so the user chooses which key to sign with
            // first
            Intent intent = new Intent(this, SelectSecretKeyListActivity.class);
            startActivityForResult(intent, Id.request.secret_keys);
        }
    }

    private void showPassphraseDialog(final long secretKeyId) {
        // Message is received after passphrase is cached
        Handler returnHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (message.what == PassphraseDialogFragment.MESSAGE_OKAY) {
                    startSigning();
                }
            }
        };

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(returnHandler);

        try {
            PassphraseDialogFragment passphraseDialog = PassphraseDialogFragment.newInstance(this,
                    messenger, secretKeyId);

            passphraseDialog.show(getSupportFragmentManager(), "passphraseDialog");
        } catch (PGPMain.ApgGeneralException e) {
            Log.d(Constants.TAG, "No passphrase for this secret key, encrypt directly!");
            // send message to handler to start encryption directly
            returnHandler.sendEmptyMessage(PassphraseDialogFragment.MESSAGE_OKAY);
        }
    }

    /**
     * handles the UI bits of the signing process on the UI thread
     */
    private void initiateSigning() {
        PGPPublicKeyRing pubring = ProviderHelper.getPGPPublicKeyRing(this, mPubKeyId);
        if (pubring != null) {
            // if we have already signed this key, dont bother doing it again
            boolean alreadySigned = false;

            @SuppressWarnings("unchecked")
            Iterator<PGPSignature> itr = pubring.getPublicKey(mPubKeyId).getSignatures();
            while (itr.hasNext()) {
                PGPSignature sig = itr.next();
                if (sig.getKeyID() == mMasterKeyId) {
                    alreadySigned = true;
                    break;
                }
            }

            if (!alreadySigned) {
                /*
                 * get the user's passphrase for this key (if required)
                 */
                String passphrase = PassphraseCacheService.getCachedPassphrase(this, mMasterKeyId);
                if (passphrase == null) {
                    showPassphraseDialog(mMasterKeyId);
                    return; // bail out; need to wait until the user has entered the passphrase
                            // before trying again
                } else {
                    startSigning();
                }
            } else {
                Toast.makeText(this, "Key has already been signed", Toast.LENGTH_SHORT).show();

                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }

    /**
     * kicks off the actual signing process on a background thread
     */
    private void startSigning() {
        // Send all information needed to service to sign key in other thread
        Intent intent = new Intent(this, ApgService.class);

        intent.putExtra(ApgService.EXTRA_ACTION, ApgService.ACTION_SIGN_KEY);

        // fill values for this action
        Bundle data = new Bundle();

        data.putLong(ApgService.SIGN_KEY_MASTER_KEY_ID, mMasterKeyId);
        data.putLong(ApgService.SIGN_KEY_PUB_KEY_ID, mPubKeyId);

        intent.putExtra(ApgService.EXTRA_DATA, data);

        // Message is received after signing is done in ApgService
        ApgServiceHandler saveHandler = new ApgServiceHandler(this, R.string.progress_signing,
                ProgressDialog.STYLE_SPINNER) {
            public void handleMessage(Message message) {
                // handle messages by standard ApgHandler first
                super.handleMessage(message);

                if (message.arg1 == ApgServiceHandler.MESSAGE_OKAY) {

                    Toast.makeText(SignKeyActivity.this, R.string.keySignSuccess,
                            Toast.LENGTH_SHORT).show();

                    // check if we need to send the key to the server or not
                    CheckBox sendKey = (CheckBox) findViewById(R.id.sendKey);
                    if (sendKey.isChecked()) {
                        /*
                         * upload the newly signed key to the key server
                         */
                        uploadKey();
                    } else {
                        finish();
                    }
                }
            };
        };

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(saveHandler);
        intent.putExtra(ApgService.EXTRA_MESSENGER, messenger);

        // show progress dialog
        saveHandler.showProgressDialog(this);

        // start service with intent
        startService(intent);
    }

    private void uploadKey() {
        // Send all information needed to service to upload key in other thread
        Intent intent = new Intent(this, ApgService.class);

        intent.putExtra(ApgService.EXTRA_ACTION, ApgService.ACTION_UPLOAD_KEY);

        // fill values for this action
        Bundle data = new Bundle();

        data.putLong(ApgService.UPLOAD_KEY_KEYRING_ID, mPubKeyId);

        Spinner keyServer = (Spinner) findViewById(R.id.keyServer);
        String server = (String) keyServer.getSelectedItem();
        data.putString(ApgService.UPLOAD_KEY_SERVER, server);

        intent.putExtra(ApgService.EXTRA_DATA, data);

        // Message is received after uploading is done in ApgService
        ApgServiceHandler saveHandler = new ApgServiceHandler(this, R.string.progress_exporting,
                ProgressDialog.STYLE_HORIZONTAL) {
            public void handleMessage(Message message) {
                // handle messages by standard ApgHandler first
                super.handleMessage(message);

                if (message.arg1 == ApgServiceHandler.MESSAGE_OKAY) {

                    Toast.makeText(SignKeyActivity.this, R.string.keySendSuccess,
                            Toast.LENGTH_SHORT).show();

                    finish();
                }
            };
        };

        // Create a new Messenger for the communication back
        Messenger messenger = new Messenger(saveHandler);
        intent.putExtra(ApgService.EXTRA_MESSENGER, messenger);

        // show progress dialog
        saveHandler.showProgressDialog(this);

        // start service with intent
        startService(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case Id.request.secret_keys: {
            if (resultCode == RESULT_OK) {
                mMasterKeyId = data.getLongExtra(EXTRA_KEY_ID, 0);

                // re-enable the sign button so the user can initiate the sign process
                Button sign = (Button) findViewById(R.id.sign);
                sign.setEnabled(true);
            }

            break;
        }

        default: {
            super.onActivityResult(requestCode, resultCode, data);
        }
        }
    }
}
