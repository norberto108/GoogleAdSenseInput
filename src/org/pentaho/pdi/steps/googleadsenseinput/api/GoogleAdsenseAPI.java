package org.pentaho.pdi.steps.googleadsenseinput.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.pdi.steps.googleadsenseinput.GoogleAdsenseInputStepMeta;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.adsense.AdSense;
import com.google.api.services.adsense.AdSense.Accounts.Reports.Generate;
import com.google.api.services.adsense.AdSenseScopes;
import com.google.api.services.adsense.model.Account;
import com.google.api.services.adsense.model.Accounts;
import com.google.api.services.adsense.model.AdsenseReportsGenerateResponse;
import com.google.api.services.adsense.model.AdsenseReportsGenerateResponse.Headers;
import com.google.api.services.adsense.model.SavedReport;
import com.google.api.services.adsense.model.SavedReports;


public class GoogleAdsenseAPI {

	
	private String CLIENT_SECRET_FILE= "";
	private String applicationName ;
	private AdSense adSense;
	private File DATA_STORE_DIR ;
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private  final int MAX_LIST_PAGE_SIZE = 50;
	

	private	FileDataStoreFactory dataStoreFactory;
	private HttpTransport httpTransport;

	private String accountId;
	private String authTokenPath;
	private String dimensions [];
	private String metrics [];
	private String filters  [];
	private String sort [];	
	private String startDate,endDate;	
	private boolean timeZone;
	private String dateRange;
	private boolean useSaveReport;
	private String saveReportID;
	private String saveReportName;
	private int rowLimit;
	  
	private static Class<?> PKG = GoogleAdsenseInputStepMeta.class;

	/*public static void main(String [] args)throws Exception
	{
		
		
	}*/
	public GoogleAdsenseAPI(String clientIDSecretFile,String appName,String authPath)throws Exception
	{
			
	      	CLIENT_SECRET_FILE = clientIDSecretFile;
			applicationName =appName;
			timeZone = true;
			authTokenPath= authPath;
			dateRange=DateRangeType.CUSTOM_DATE;
			
			createAdsenseSession();
	}
	private void createAdsenseSession()throws Exception
	{	
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
			        new InputStreamReader(new FileInputStream(new File(CLIENT_SECRET_FILE))));
			    
	    if (clientSecrets.getDetails().getClientId().startsWith("Enter")
	        || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
	     
	    	throw new Exception(BaseMessages.getString( PKG, "GoogleAdSenseAPI.InvalidClientSecrentFile.message" ));
	    }
		
	    httpTransport = GoogleNetHttpTransport.newTrustedTransport();
	    DATA_STORE_DIR = new File(authTokenPath +"/"+ clientSecrets.getDetails().getClientId());
      	dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
	    
	    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
		    httpTransport, JSON_FACTORY, clientSecrets,
		    Collections.singleton(AdSenseScopes.ADSENSE_READONLY)).setDataStoreFactory(
		    dataStoreFactory).build();
			    // authorize
			    
	    Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
	    adSense = new AdSense.Builder(
		        new NetHttpTransport(), JSON_FACTORY, credential).setApplicationName(applicationName)
		        .build();
	}
	
	public List<String> getAllAccountId()throws Exception
	{
		List<String> accIds=  new ArrayList<String>();
		String pageToken = null;
		Accounts accounts = null;
		do {
		  accounts = adSense.accounts().list()
			  .setMaxResults(MAX_LIST_PAGE_SIZE)
		      .setPageToken(pageToken)
		      .execute();
		
		  if (accounts.getItems() != null && !accounts.getItems().isEmpty()) {
		    for (Account account : accounts.getItems()) {
		    	accIds.add(account.getId());
		    }
		  } 
		  pageToken = accounts.getNextPageToken();
		} while (pageToken != null);
		
		return accIds;
		
	}
	public HashMap<String,String> getAllSaveReports()throws Exception
	{
		HashMap<String,String> reports=new HashMap<String,String>();  
		
		String pageToken = null;
	    SavedReports savedReports = null;
	    do {
	      savedReports = adSense.accounts().reports().saved().list(accountId)
	          .setMaxResults(MAX_LIST_PAGE_SIZE)
	          .setPageToken(pageToken)
	          .execute();

	      if (savedReports.getItems() != null && !savedReports.getItems().isEmpty()) {
	        for (SavedReport savedReport : savedReports.getItems()) {
	        		reports.put(savedReport.getId(), savedReport.getName());	              
	        }
	      } 

	      pageToken = savedReports.getNextPageToken();
	    } while (pageToken != null);

		return reports;
		
	}
	public List<Headers> getReportHeader()throws Exception
	{
		AdsenseReportsGenerateResponse response;
		
		if(useSaveReport)
		{
			com.google.api.services.adsense.AdSense.Accounts.Reports.Saved.Generate request =adSense.accounts().reports().saved().generate(accountId, saveReportID);
			request.setMaxResults(0);
			response = request.execute();
		}
		else
		{
			Generate request = getReportGenerater();
			request.setMaxResults(0);
			response = request.execute();
		}
		
		// Check if the results fit the requirements for this method.
	    if (response.getHeaders() == null && !response.getHeaders().isEmpty()) {
	      throw new RuntimeException("No headers defined in report results.");
	    }

	    if (response.getHeaders().size() < 2 ||
	        !response.getHeaders().get(0).getType().equals("DIMENSION")) {
	      throw new RuntimeException("Insufficient dimensions and metrics defined.");
	    }
	    
		return response.getHeaders(); 

	}
	private Generate getReportGenerater()throws Exception
	{
		if(!dateRange.equalsIgnoreCase(DateRangeType.CUSTOM_DATE))
		{
			String val [] = DateRangeType.getStartEndDate(dateRange);
			startDate = val[0];
			endDate = val[1];
		}
		
		Generate request = adSense.accounts().reports().generate(accountId, startDate, endDate);

		request.setDimension(Arrays.asList(dimensions));
		request.setMetric(Arrays.asList(metrics));

		request.setFilter(Arrays.asList(filters));
		request.setSort(Arrays.asList(sort));
		request.setUseTimezoneReporting(timeZone);
		
		return request;
	}


	public String validate()
	{
		if(adSense == null)
			return BaseMessages.getString( PKG, "GoogleAdsenseDialog.AuthenticationFailure.DialogMessage" ) ;
		else if(dimensions == null || dimensions.length < 1)
			return BaseMessages.getString( PKG, "GoogleAdsenseStep.GoogleAdsenseAPI.InvalidDimension" ) ;
		else if(metrics == null || metrics.length < 1)
			return BaseMessages.getString( PKG, "GoogleAdsenseStep.GoogleAdsenseAPI.InvalidMetrics" ) ;
		else if(dateRange == null )
			return BaseMessages.getString( PKG, "GoogleAdsenseStep.GoogleAdsenseAPI.InvalidDateRange" ) ;
		else if(dateRange.equalsIgnoreCase(DateRangeType.CUSTOM_DATE))
		{
			if(startDate == null )
				return BaseMessages.getString( PKG, "GoogleAdsenseStep.GoogleAdsenseAPI.InvalidStartDate" ) ;
			else if (endDate == null)
				return BaseMessages.getString( PKG, "GoogleAdsenseStep.GoogleAdsenseAPI.InvalidEndDate" ) ;
		}
		return BaseMessages.getString( PKG, "GoogleAdsenseStep.GoogleAdsenseAPI.Success" ) ;
		
	}
	
	public List<List<String>>  runRerpots()throws Exception
	{
	
		AdsenseReportsGenerateResponse response;
		
		if(useSaveReport)
		{
			if(saveReportID == null || saveReportID.length() <1)
				throw new KettleException(BaseMessages.getString( PKG, "GoogleAdsenseStep.GoogleAdsenseAPI.InvalidSaveReport" ));	
			else
			{
				com.google.api.services.adsense.AdSense.Accounts.Reports.Saved.Generate request =adSense.accounts().reports().saved().generate(accountId, saveReportID);
				if(rowLimit >0)
					request.setMaxResults(rowLimit);
				response = request.execute();
			}
		}
		else
		{
			String validMsg=validate(); 
			if(!validMsg.equalsIgnoreCase(BaseMessages.getString( PKG, "GoogleAdsenseStep.GoogleAdsenseAPI.Success" )))
			{
				throw new KettleException(validMsg);
			}
			
			Generate request = getReportGenerater();
			if(rowLimit >0)
				request.setMaxResults(rowLimit);
			response = request.execute();
		}
		List<List<String>> rows = new ArrayList<List<String>>();
	    if(response.getRows()!=null )
	    	rows =response.getRows();    
	    return rows;   
	}
	
	public String[] getDimensions() {
		return dimensions;
	}
	public void setDimensions(String[] dimensions) {
		this.dimensions = dimensions;
	}
	public void setDimensions(String dimensions) {
	
		if(dimensions.length() > 0)
		{
			String dimensionArr[] = dimensions.split(",");
			this.dimensions = new String[dimensionArr.length];
			for(int i=0;i<dimensionArr.length;i++)
			{
				this.dimensions [i] = dimensionArr[i]  ;
			}
		}
		else
		{
			this.dimensions = new String[]{};
		}
		
		
	}
	public String[] getMetrics() {
		return metrics;
	}
	public void setMetrics(String[] metrics) {
		this.metrics = metrics;
	}
	public void setMetrics(String metrics) {
		
		if(metrics.length() > 0)
		{
			String metricsArr[] = metrics.split(",");
			this.metrics = new String[metricsArr.length];
			
			for(int i=0;i<metricsArr.length;i++)
			{
				this.metrics [i] = metricsArr[i]  ;
			}
		}
		else
		{
			this.metrics = new String[]{};
		}
	}
	public String[] getFilters() {
		return filters;
	}
	public void setFilters(String[] filters) {
		this.filters = filters;
	}
	public void setFilters(String filters) {
		
		if(filters!= null && filters.length() > 0)
		{
			//String filtersStr= filters.replaceAll("(?i)or", ","); // OR Condition 
			String filtersArr []= filters.split("&"); // And Condition
			
			this.filters = new String[filtersArr.length];
			for(int i=0;i<filtersArr.length;i++)
			{
				this.filters [i] = filtersArr[i]  ;
			}
		}
		else
		{
			this.filters = new String[]{};
		}
		
	}
	public String[] getSort() {
		return sort;
	}
	public void setSort(String[] sort) {
		this.sort = sort;
	}
	public void setSort(String sort) {
		
		if(sort != null && sort.length() > 0)
		{
			String sortArr[] = sort.split(",");
			this.sort = new String[sortArr.length];
			
			for(int i=0;i<sortArr.length;i++)
			{
				this.sort [i] = sortArr[i]  ;
			}
		}
		else
		{
			this.sort = new String[]{};
		}
	}
	public String getStartDate() {
		return startDate;
	}
	
	public void setStartDate(String startDate) {
		this.startDate = startDate;
	}
	public String getEndDate() {
		return endDate;
	}
	public void setEndDate(String endDate) {
		this.endDate = endDate;
	}
	
	public String getDateRange() {
		return dateRange;
	}
	public void setDateRange(String dateRange) {
		this.dateRange = dateRange;
	}
	public String getApplicationName() {
		return applicationName;
	}
	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}
	public boolean isTimeZone() {
		return timeZone;
	}
	public void setTimeZone(boolean timeZone) {
		this.timeZone = timeZone;
	}
	public String getAccountId() {
		return accountId;
	}
	public void setAccountId(String accountId) {
		this.accountId = accountId;
	}
	public boolean isUseSaveReport() {
		return useSaveReport;
	}
	public void setUseSaveReport(boolean useSaveReport) {
		this.useSaveReport = useSaveReport;
	}
	public String getSaveReportID() {
		return saveReportID;
	}
	public void setSaveReportID(String saveReportID) {
		this.saveReportID = saveReportID;
	}
	public String getSaveReportName() {
		return saveReportName;
	}
	public void setSaveReportName(String saveReportName) {
		this.saveReportName = saveReportName;
	}
	public int getRowLimit() {
		return rowLimit;
	}
	public void setRowLimit(int rowLimit) {
		this.rowLimit = rowLimit;
	}
	public String getAuthTokenPath() {
		return authTokenPath;
	}
	public void setAuthTokenPath(String authTokenPath) {
		this.authTokenPath = authTokenPath;
	}
	
	
}
