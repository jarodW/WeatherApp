package com.example.android.sunshine.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {
    ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {
    }

    @Override
    public void onStart(){
        super.onStart();
        updateWeather();
    }

    @Override
     public void onCreate(Bundle savedInstanceState) {
              super.onCreate(savedInstanceState);
              // Add this line in order for this fragment to handle menu events.
              setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater){
        inflater.inflate(R.menu.forecastfragment,menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();
        if(id==R.id.action_refresh){
            updateWeather();
            return true;
        }
        if(id==R.id.action_map){
            openMap();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateWeather(){
        FetchWeatherTask fetchWeather = new FetchWeatherTask();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = preferences.getString(getString(R.string.pref_location_key),getString(R.string.pref_location_default));
        fetchWeather.execute(location);
    }

    private void openMap(){
        SharedPreferences preference = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = preference.getString(getString(R.string.pref_location_key),getString(R.string.pref_location_default));
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri intentUri = Uri.parse("geo:0,0?").buildUpon().appendQueryParameter("q",location).build();
        intent.setData(intentUri);
        startActivity(intent);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        String[] data = {

        };
        List<String> weekForecast = new ArrayList<String>(Arrays.asList(data));
        mForecastAdapter  = new ArrayAdapter<String>(getActivity(),R.layout.list_item_forecast,R.id.list_item_forecast_textview,weekForecast);
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                //Toast toast = Toast.makeText(getContext(),mForecastAdapter.getItem(i),Toast.LENGTH_SHORT);
                //toast.show();
                Intent intent = new Intent(getActivity(),DetailActivity.class).putExtra(Intent.EXTRA_TEXT,mForecastAdapter.getItem(i));
                startActivity(intent);

            }
        });

        return rootView;
    }

    public class FetchWeatherTask extends AsyncTask<String,Void,String[]>{

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();


        private String getReadableDateString(long time){
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);

        }


        private String formatHighLows(double high, double low){

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unit = preferences.getString(getString(R.string.pref_unit_list_key),getString(R.string.pref_unit_list_default_value));
            if(unit.equalsIgnoreCase("0")){
                high = high * (9/5) + 32;
                low = low *(9/5) + 32;
            }
            long roundHigh = Math.round(high);
            long roundLow = Math.round(low);
            String highLowStr = roundHigh + "/" + roundLow;
            return highLowStr;
        }

        private  String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)throws JSONException{
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.optJSONArray(OWM_LIST);

            Time dayTime = new Time();
            dayTime.setToNow();

            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(),dayTime.gmtoff);

            dayTime = new Time();

            String[] resultStrs = new String[numDays];
            for(int i = 0;i<weatherArray.length();i++){
                String day;
                String description;
                String highAndLow;

                JSONObject dayForecast = weatherArray.getJSONObject(i);

                long dateTime = dayTime.setJulianDay(julianStartDay + i);
                day = getReadableDateString(dateTime);

                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high,low);

                resultStrs[i] = day + " - " + description + " - " + highAndLow;

            }
            return resultStrs;
        }

        @Override
        protected String[] doInBackground(String ... params){
            if(params.length == 0)
                return null;

            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            String forecastJsonStr = null;

            String format = "json";
            String units = "metric";
            int cnt = 7;
            String appId = "3751a5bf9378f7ac351975ec3e734bd0";

            try{
                //construct url
                final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";
                final String APPID_PARAM = "APPID";

                Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon().appendQueryParameter(QUERY_PARAM,params[0]).appendQueryParameter(FORMAT_PARAM,format).appendQueryParameter(UNITS_PARAM,units).appendQueryParameter(DAYS_PARAM,Integer.toString(cnt)).appendQueryParameter(APPID_PARAM,appId).build();
                URL url = new URL(builtUri.toString());
                //create request and open connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                //Read input into String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();

                if(inputStream == null)
                    return null;

                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while((line = reader.readLine()) != null){
                    buffer.append(line + "\n");
                }

                if(buffer.length() == 0)
                    return null;

                forecastJsonStr = buffer.toString();
                Log.v(LOG_TAG,"Uri: " + url.toString());
            }catch (IOException e){
                Log.e(LOG_TAG,"Error",e);
                return null;
            }finally{
                if(urlConnection != null)
                    urlConnection.disconnect();
                if(reader != null)
                    try{
                        reader.close();
                    }catch (final IOException e){
                        Log.e(LOG_TAG,"Error closing stream", e);
                    }
            }

            try{
                return getWeatherDataFromJson(forecastJsonStr,cnt);
            }catch(JSONException e){
                Log.e(LOG_TAG,e.getMessage(),e);
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String[] strings) {
            super.onPostExecute(strings);
            List<String> weekForecast = new ArrayList<String>(Arrays.asList(strings));
            mForecastAdapter.clear();
            mForecastAdapter.addAll(weekForecast);
            mForecastAdapter.notifyDataSetChanged();

        }
    }
}
