package colx.org.colxwallet.utils;

import org.colxj.core.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import colx.org.colxwallet.LogHelper;
import colx.org.colxwallet.contacts.AddressLabel;
import colx.org.colxwallet.module.PivxModule;
import colx.org.colxwallet.ui.wallet_activity.TransactionWrapper;
import global.ILogHelper;

/**
 * Created by furszy on 8/14/17.
 */

public class TxUtils {

    private static ILogHelper logger = LogHelper.getLogHelper(TxUtils.class);

    public static String getAddressOrContact(PivxModule pivxModule, TransactionWrapper data) {
        String text;
        if (data.getOutputLabels()!=null && !data.getOutputLabels().isEmpty()){
            Collection<AddressLabel> addressLabels = data.getOutputLabels().values();
            AddressLabel addressLabel = addressLabels.iterator().next();
            if (addressLabel !=null) {
                if (addressLabel.getName() != null)
                    text = addressLabel.getName();
                else
                    text = addressLabel.getAddresses().get(0);
            }else {
                try {
                    text = data.getTransaction().getOutput(0).getScriptPubKey().getToAddress(pivxModule.getConf().getNetworkParams(), true).toBase58();
                }catch (ScriptException e){
                    text = data.getTransaction().getOutput(1).getScriptPubKey().getToAddress(pivxModule.getConf().getNetworkParams(),true).toBase58();
                }
            }
        }else {
            text = "Error";
            logger.warn(data.toString());
        }
        return text;
    }

}
