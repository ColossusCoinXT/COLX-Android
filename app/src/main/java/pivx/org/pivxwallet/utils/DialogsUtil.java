package pivx.org.pivxwallet.utils;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import pivtrum.PivtrumPeerData;
import pivx.org.pivxwallet.R;
import pivx.org.pivxwallet.module.PivxContext;
import pivx.org.pivxwallet.ui.base.dialogs.SimpleTextDialog;
import pivx.org.pivxwallet.ui.base.dialogs.SimpleTwoButtonsDialog;

/**
 * Created by furszy on 7/5/17.
 */

public class DialogsUtil {


    public static SimpleTextDialog buildSimpleErrorTextDialog(Context context, String title, String body){
        final SimpleTextDialog dialog = SimpleTextDialog.newInstance();
        dialog.setTitle(title);
        dialog.setBody(body);
        dialog.setOkBtnBackgroundColor(Color.RED);
        dialog.setOkBtnTextColor(Color.WHITE);
        dialog.setRootBackgroundRes(R.drawable.dialog_bg);
        return dialog;
    }

    public static SimpleTextDialog buildSimpleTextDialog(Context context, String title, String body){
        final SimpleTextDialog dialog = SimpleTextDialog.newInstance();
        dialog.setTitle(title);
        dialog.setBody(body);
        dialog.setOkBtnBackgroundColor(context.getResources().getColor(R.color.lightGreen,null));
        dialog.setOkBtnTextColor(Color.WHITE);
        dialog.setRootBackgroundRes(R.drawable.dialog_bg);
        return dialog;
    }

    public static SimpleTwoButtonsDialog buildSimpleTwoBtnsDialog(Context context, String title, String body, SimpleTwoButtonsDialog.SimpleTwoBtnsDialogListener simpleTwoBtnsDialogListener){
        final SimpleTwoButtonsDialog dialog = SimpleTwoButtonsDialog.newInstance(context);
        dialog.setTitle(title);
        dialog.setTitleColor(Color.BLACK);
        dialog.setBody(body);
        dialog.setBodyColor(Color.BLACK);
        dialog.setListener(simpleTwoBtnsDialogListener);
        dialog.setContainerBtnsBackgroundColor(Color.WHITE);
        dialog.setRightBtnBackgroundColor(context.getResources().getColor(R.color.lightGreen,null));
        dialog.setLeftBtnTextColor(Color.BLACK);
        dialog.setRightBtnTextColor(Color.WHITE);
        dialog.setRootBackgroundRes(R.drawable.dialog_bg);
        return dialog;
    }


    public interface TrustedNodeDialogListener{
        void onNodeSelected(PivtrumPeerData pivtrumPeerData);
    }

    public static DialogBuilder buildtrustedNodeDialog(Context context, final TrustedNodeDialogListener trustedNodeDialogListener){
        LayoutInflater content = LayoutInflater.from(context);
        View dialogView = content.inflate(R.layout.dialog_node, null);
        DialogBuilder nodeDialog = new DialogBuilder(context);
        final EditText editHost = (EditText) dialogView.findViewById(R.id.hostText);
        final EditText editTcp = (EditText) dialogView.findViewById(R.id.tcpText);
        final EditText editSsl = (EditText) dialogView.findViewById(R.id.sslText);
        nodeDialog.setTitle("Add your Node");
        nodeDialog.setView(dialogView);
        nodeDialog.setPositiveButton("Add Node", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String host = editHost.getText().toString();
                String tcpPortStr = editTcp.getText().toString();
                String sslPortStr = editSsl.getText().toString();
                int tcpPort = PivxContext.NETWORK_PARAMETERS.getPort();
                int sslPort = 0;
                if (tcpPortStr.length()>0){
                    tcpPort = Integer.valueOf(tcpPortStr);
                }
                if (sslPortStr.length()>0){
                    sslPort = Integer.valueOf(sslPortStr);
                }
                trustedNodeDialogListener.onNodeSelected(
                        new PivtrumPeerData(
                                host,
                                tcpPort,
                                sslPort)
                );
                dialog.dismiss();
            }
        });
        nodeDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        return nodeDialog;
    }

}
