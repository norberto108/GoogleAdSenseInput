
package org.pentaho.pdi.steps.googleadsenseinput;

import java.util.List;

import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.trans.step.BaseStepData;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.pdi.steps.googleadsenseinput.api.GoogleAdsenseAPI;


public class GoogleAdsenseInputStepData extends BaseStepData implements StepDataInterface {

	public RowMetaInterface outputRowMeta;
	public ValueMetaInterface[] conversionMeta;
	public List<List <String>> rows;
	public int entryIndex;
	public  GoogleAdsenseAPI gAPI; 
    public GoogleAdsenseInputStepData()
	{
		super();
	}
}
	
