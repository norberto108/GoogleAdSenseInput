
package org.pentaho.pdi.steps.googleadsenseinput;


import java.util.List;

import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowDataUtil;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.row.value.ValueMetaFactory;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStep;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.pentaho.pdi.steps.googleadsenseinput.api.DateRangeType;
import org.pentaho.pdi.steps.googleadsenseinput.api.GoogleAdsenseAPI;



public class GoogleAdsenseInputStep extends BaseStep implements StepInterface {

	
	GoogleAdsenseInputStepMeta meta ;
	GoogleAdsenseInputStepData data ;
	private static Class<?> PKG = GoogleAdsenseInputStepMeta.class; // for i18n purposes
	
	public GoogleAdsenseInputStep(StepMeta s, StepDataInterface stepDataInterface, int c, TransMeta t, Trans dis) {
		super(s, stepDataInterface, c, t, dis);
	}
	
	public boolean init(StepMetaInterface smi, StepDataInterface sdi) {
		// Casting to step-specific implementation classes is safe
		GoogleAdsenseInputStepMeta meta = (GoogleAdsenseInputStepMeta) smi;
		GoogleAdsenseInputStepData data = (GoogleAdsenseInputStepData) sdi;
		if(!super.init(meta, data))
		{
			return false;
		}

		
		return true;
	}	

	public boolean processRow(StepMetaInterface smi, StepDataInterface sdi) throws KettleException {

		meta = (GoogleAdsenseInputStepMeta) smi;
		data = (GoogleAdsenseInputStepData) sdi;
		
		if (first) {
			
			first = false;
			data.outputRowMeta = new RowMeta();
			meta.getFields( data.outputRowMeta, getStepname(), null, null, this, repository, metaStore );
			
			data.conversionMeta = new ValueMetaInterface[ meta.getFeedField().length ];

			for ( int i = 0; i < meta.getFeedField().length; i++ ) {
	

		        ValueMetaInterface returnMeta = data.outputRowMeta.getValueMeta( i );
	
		        ValueMetaInterface conversionMeta;
	
		        conversionMeta = ValueMetaFactory.cloneValueMeta( returnMeta, ValueMetaInterface.TYPE_STRING );
		        conversionMeta.setConversionMask( meta.getConversionMask()[ i ] );
		        conversionMeta.setDecimalSymbol( "." ); 
		        conversionMeta.setGroupingSymbol( null ); 
	
		        data.conversionMeta[ i ] = conversionMeta;
		      }
			
	      try
			{
				data.gAPI = new GoogleAdsenseAPI(environmentSubstitute(meta.getOAuthKeyFile()),environmentSubstitute(meta.getAppName()),environmentSubstitute(meta.getAuthTokenPath()));

				if (log.isBasic()) logBasic(BaseMessages.getString( PKG, "GoogleAdsenseStep.AuthenticationSuccess.Message" ) );
				
				data.gAPI.setAccountId(meta.getAccountID());
				
				
				if(meta.isUseSaveReport())
				{
					data.gAPI.setUseSaveReport(meta.isUseSaveReport());
					data.gAPI.setSaveReportID(meta.getSaveReportID());
					data.gAPI.setSaveReportName(meta.getSaveReportName());
				}
				else
				{
					data.gAPI.setTimeZone(meta.isTimeZone());
					data.gAPI.setDateRange(meta.getDateType());
					if(data.gAPI.getDateRange().equalsIgnoreCase(DateRangeType.CUSTOM_DATE))
					{
						data.gAPI.setStartDate(environmentSubstitute(meta.getStartDate()));
						data.gAPI.setEndDate(environmentSubstitute(meta.getEndDate()));
					}
					data.gAPI.setDimensions(environmentSubstitute(meta.getDimensions()));
					data.gAPI.setMetrics(environmentSubstitute(meta.getMetrics()));
					data.gAPI.setFilters(environmentSubstitute(meta.getFilters()));
					data.gAPI.setSort(environmentSubstitute(meta.getSort()));
				}
				
				data.gAPI.setRowLimit(meta.getRowLimit());
				
				if(isDetailed())
					logDetailed("Configuration Properties : "+ meta.getXML());
			}
			catch(Exception e)
			{
				logError(e.getMessage() );
				e.printStackTrace();
				setErrors(1);
				stopAll();
			}
			
		}
		
		Object[] outputRow = RowDataUtil.allocateRowData( data.outputRowMeta.size() );
		List <String> entry  = getNextDataEntry();
		
		if ( entry != null) { 

			for ( int i = 0; i < meta.getFeedField().length; i++ ) {
				
				Object dataObject = entry.get(i);
		        outputRow[ i ] = data.outputRowMeta.getValueMeta( i ).convertData( data.conversionMeta[ i ], dataObject );
		      }

			putRow( data.outputRowMeta, outputRow );
			
			if ( checkFeedback( getLinesWritten() ) ) {
		        if ( log.isBasic() ) {
		          logBasic( "Linenr " + getLinesWritten() );
		        }
		      }
		      return true;

		} 
		else {
			setOutputDone();
			return false;
		}
	}

	public void dispose(StepMetaInterface smi, StepDataInterface sdi) {

		GoogleAdsenseInputStepMeta meta = (GoogleAdsenseInputStepMeta) smi;
		GoogleAdsenseInputStepData data = (GoogleAdsenseInputStepData) sdi;
		
		super.dispose(meta, data);
	}

	private List<String> getNextDataEntry() throws KettleException {
	     
		if ( data.rows == null ) {
			
			try {
				
				if ( log.isBasic() ) 
					logBasic( "Retriving Google Adsense data..." );
				
				data.rows = data.gAPI.runRerpots();
				
				if ( log.isBasic() ) 
					logBasic( "Google Adsense data retrieved successfully..." );
				if ( log.isDetailed() ) {
					logDetailed( "Report successfully downloaded : " + data.rows.size() + " Rows Featched.");
				}
				data.entryIndex = 0;
			} catch ( Exception e2 ) {
		    	e2.printStackTrace();
		    	logError(e2.getMessage());
		        throw new KettleException( e2 );
			}

		}
	    if (data.entryIndex < data.rows.size()  ) {
	      incrementLinesInput();
	      
	      if(meta.getRowLimit()<1 || data.entryIndex < meta.getRowLimit())
	    	  return data.rows.get( data.entryIndex++ );
	      else
	    	  return null;
	    } 
	    else {
	      return null;
	    }
		
	}
}
