package colx.org.colxwallet.rate;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import colx.org.colxwallet.rate.db.PivxRate;

/**
 * Created by furszy on 7/5/17.
 */

public class CoinMarketCapApiClient {

    public class PivxMarket{
        public BigDecimal priceUsd;
        public BigDecimal priceBtc;
        public BigDecimal marketCapUsd;
        public BigDecimal totalSupply;
        public int rank;

        public PivxMarket(BigDecimal priceUsd, BigDecimal priceBtc, BigDecimal marketCapUsd, BigDecimal totalSupply, int rank) {
            this.priceUsd = priceUsd;
            this.priceBtc = priceBtc;
            this.marketCapUsd = marketCapUsd;
            this.totalSupply = totalSupply;
            this.rank = rank;
        }
    }

    public PivxMarket getPivxPxrice() throws RequestPivxRateException{
        try {
            String cryptoId = "2001";
            String url = "https://pro-api.coinmarketcap.com/v1/cryptocurrency/quotes/latest";
            String resultUSD = makeCoinmarketAPICall(url, cryptoId, "USD");
            String resultBTC = makeCoinmarketAPICall(url, cryptoId, "BTC");

            JSONObject latestUsdJSON = new JSONObject(resultUSD);
            if (!latestUsdJSON.has("data"))
                return null;

            JSONObject cryptoIdJSON = latestUsdJSON.getJSONObject("data").getJSONObject(cryptoId);
            int rank = cryptoIdJSON.getInt("cmc_rank");
            String totalSupply = cryptoIdJSON.getString("total_supply");

            JSONObject usdJSON = cryptoIdJSON.getJSONObject("quote").getJSONObject("USD");
            String priceUSD = usdJSON.getString("price");
            String marketCapUSD = usdJSON.getString("market_cap");

            String priceBTC = "0";
            JSONObject latestBtcJSON = new JSONObject(resultBTC);
            if (latestBtcJSON.has("data"))
                priceBTC = latestBtcJSON.getJSONObject("data")
                        .getJSONObject(cryptoId)
                        .getJSONObject("quote")
                        .getJSONObject("BTC")
                        .getString("price");

            return new PivxMarket(
                    new BigDecimal(priceUSD),
                    new BigDecimal(priceBTC),
                    new BigDecimal(marketCapUSD),
                    new BigDecimal(totalSupply),
                    rank);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            throw new RequestPivxRateException(e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RequestPivxRateException(e);
        } catch (JSONException e) {
            e.printStackTrace();
            throw new RequestPivxRateException(e);
        }
    }

    public static String makeCoinmarketAPICall(String addr, String cryptoId, String convert) throws IOException {
        if (addr.isEmpty())
            throw new IllegalArgumentException("addr");
        if (cryptoId.isEmpty())
            throw new IllegalArgumentException("cryptoId");

        HttpUrl.Builder urlBuilder = HttpUrl.parse(addr).newBuilder();
        urlBuilder.addQueryParameter("id", cryptoId);
        urlBuilder.addQueryParameter("convert", convert.isEmpty() ? "USD" : convert);
        String url = urlBuilder.build().toString();

        Request request = new Request.Builder()
                .header("Accept", "application/json")
                .header("X-CMC_PRO_API_KEY", "ae725c04-bbd6-49cf-9190-67c49ff7672f")
                .url(url)
                .build();

        OkHttpClient client = new OkHttpClient();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    public static HttpResponse get(String url) throws IOException {
        BasicHttpParams basicHttpParams = new BasicHttpParams();
        HttpConnectionParams.setSoTimeout(basicHttpParams, (int) TimeUnit.MINUTES.toMillis(1));
        HttpClient client = new DefaultHttpClient(basicHttpParams);
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader("Accept", "text/html,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5");
        httpGet.setHeader("Content-type", "application/json");
        return client.execute(httpGet);
    }


    public static String convertInputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream,"ISO-8859-1"));
        String line = "";
        String result = "";
        while((line = bufferedReader.readLine()) != null)
            result += line;

        inputStream.close();
        return result;
    }

    public static class BitPayApi {

        private final String URL = "https://bitpay.com/rates";

        public interface RatesConvertor<T>{

            T convertRate(String code, String name, BigDecimal bitcoinRate);

        }

        /**
         * {"code":"BTC","name":"Bitcoin","rate":1}
         * @return
         * @throws RequestPivxRateException
         */
        public <T> List<T> getRates(RatesConvertor<T> ratesConvertor) throws RequestPivxRateException{
            try {
                HttpResponse httpResponse = get(URL);
                // receive response as inputStream
                InputStream inputStream = httpResponse.getEntity().getContent();
                String result = null;
                List<T> ret = new ArrayList<>();
                if (inputStream != null)
                    result = convertInputStreamToString(inputStream);
                if (httpResponse.getStatusLine().getStatusCode()==200){
                    JSONArray jsonArray = new JSONObject(result).getJSONArray("data");
                    for (int i=0; i < jsonArray.length(); i++){
                        JSONObject jsonObject = jsonArray.getJSONObject(i);
                        String code = jsonObject.getString("code");
                        String name = jsonObject.getString("name");
                        BigDecimal rate = new BigDecimal(jsonObject.getString("rate"));
                        ret.add(ratesConvertor.convertRate(code,name,rate));
                    }
                }
                return ret;
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                throw new RequestPivxRateException(e);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RequestPivxRateException(e);
            } catch (JSONException e) {
                e.printStackTrace();
                throw new RequestPivxRateException(e);
            }
        }

    }

}
