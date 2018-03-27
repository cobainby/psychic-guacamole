package com.southgt.smosplat.project.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.southgt.smosplat.common.dao.IBaseDao;
import com.southgt.smosplat.common.service.impl.BaseServiceImpl;
import com.southgt.smosplat.data.service.ILZService;
import com.southgt.smosplat.project.dao.ISurveyPoint_LZDao;
import com.southgt.smosplat.project.entity.Project;
import com.southgt.smosplat.project.entity.SurveyPoint_LZ;
import com.southgt.smosplat.project.service.IMonitorItemService;
import com.southgt.smosplat.project.service.ISurveyPoint_LZService;
import com.southgt.smosplat.project.service.IWarningService;

@Service("sp_LZService")
public class SurveyPoint_LZServiceImpl extends BaseServiceImpl<SurveyPoint_LZ> implements ISurveyPoint_LZService {

	@Resource
	IMonitorItemService monitorItemService;
	
	@Resource
	IWarningService warningService;
	
	@Resource
	ILZService lzService;
	
	@Resource(name="sp_LZDao")
	@Override
	public void setDao(IBaseDao<SurveyPoint_LZ> dao) {
		super.setDao(dao);
	}

	
	@Override
	public List<SurveyPoint_LZ> addSP_LZ(Project project, SurveyPoint_LZ tempSP, int spCount,int beginNum) throws Exception {
		String codeChar=tempSP.getCodeChar();
		String monitorItemUuid=tempSP.getMonitorItem().getMonitorItemUuid();
		String warningUuid=tempSP.getWarning().getWarningUuid();
		
		Float originalTotalValue = tempSP.getOriginalTotalValue();

		List<SurveyPoint_LZ> addSps = new ArrayList<SurveyPoint_LZ>();
		//根据编号获取所有该编号的监测点		
		List<SurveyPoint_LZ> existedSPs=((ISurveyPoint_LZDao)getDao()).getExistedSurveyPoint_LZsByCode(project.getProjectUuid(),monitorItemUuid,codeChar);
		
		if(existedSPs.size()==0){
			for (int i = 0; i < spCount; i++) {
				int number=beginNum+i;
				SurveyPoint_LZ sp=new SurveyPoint_LZ();
				sp.setCode(codeChar+number);
				sp.setCodeChar(codeChar);
				sp.setOriginalTotalValue(originalTotalValue);
				sp.setDeviceType(tempSP.getDeviceType());
				
				sp.setProject(project);
				sp.setMonitorItem(monitorItemService.getEntity(monitorItemUuid));
				sp.setWarning(warningService.getEntity(warningUuid));
				getDao().saveEntity(sp);
				addSps.add(sp);
			}
		}else{
			//获取已经存在的测点号数
			List<Integer> existedNumbers=new ArrayList<Integer>();
			for(int i=0;i<existedSPs.size();i++){
				existedNumbers.add(Integer.parseInt(existedSPs.get(i).getCode().replace(codeChar, "")));
			}
			//判断输入的连续序号数与已经存在的序号是否有冲突
			List<Integer> numbers=new ArrayList<Integer>();
			String tips="";
			for(int i=0;i<spCount;i++){
				int number=beginNum+i;
				if(existedNumbers.contains(number)){
					if(i<spCount-1){
						tips=tips+tempSP.getCodeChar()+number+"、";
					}else{
						tips=tips+tempSP.getCodeChar()+number;
					}
				}else{
					numbers.add(beginNum+i);
				}
			}
			//存在错误点号则组织返回错误的点号，否则逐一添加
			if(!tips.equals("")){
				throw new Exception("添加失败！已存在测点编号为:"+tips+"的测点,请重新确认");
			}else{
				for (int i = 0; i < numbers.size(); i++) {
					SurveyPoint_LZ sp=new SurveyPoint_LZ();
					sp.setCode(codeChar+numbers.get(i));
					sp.setCodeChar(codeChar);
					sp.setOriginalTotalValue(originalTotalValue);
					sp.setDeviceType(tempSP.getDeviceType());
					
					sp.setProject(project);
					sp.setMonitorItem(monitorItemService.getEntity(monitorItemUuid));
					sp.setWarning(warningService.getEntity(warningUuid));
					getDao().saveEntity(sp);
					addSps.add(sp);
				}
			}
		}
		return addSps;
	}

	@Override
	public List<SurveyPoint_LZ> getSP_LZs(String projectUuid, String monitorItemUuid) {
		List<SurveyPoint_LZ> sps=((ISurveyPoint_LZDao)getDao()).getSurveyPoint_LZs(projectUuid,monitorItemUuid);
		//需要按照测点编号进行排序
		sps.sort(new Comparator<SurveyPoint_LZ>() {
			@Override
			public int compare(SurveyPoint_LZ sp1, SurveyPoint_LZ sp2) {  
                int compareCode = sp1.getCodeChar().compareTo(sp2.getCodeChar());  
                if (compareCode == 0) {
                	int number1=Integer.parseInt(sp1.getCode().replace(sp1.getCodeChar(), ""));
    				int number2=Integer.parseInt(sp2.getCode().replace(sp2.getCodeChar(), ""));
                    return (number1 == number2 ? 0 : (number1 > number2 ? 1 : -1));  
                }  
                return compareCode;  
            }
		});
		return sps;
	}
	
	@Override
	public List<SurveyPoint_LZ> getSP_LZs(String projectUuid) {
		List<SurveyPoint_LZ> sps=((ISurveyPoint_LZDao)getDao()).getSurveyPoint_LZs(projectUuid);
		//需要按照测点编号进行排序
		sps.sort(new Comparator<SurveyPoint_LZ>() {
			@Override
			public int compare(SurveyPoint_LZ sp1, SurveyPoint_LZ sp2) {  
                int compareCode = sp1.getCodeChar().compareTo(sp2.getCodeChar());  
                if (compareCode == 0) {
                	int number1=Integer.parseInt(sp1.getCode().replace(sp1.getCodeChar(), ""));
    				int number2=Integer.parseInt(sp2.getCode().replace(sp2.getCodeChar(), ""));
                    return (number1 == number2 ? 0 : (number1 > number2 ? 1 : -1));  
                }  
                return compareCode;  
            }
		});
		return sps;
	}

	@Override
	public void updateSP_LZ(Project project, SurveyPoint_LZ surveyPoint) throws Exception {
		long num=((ISurveyPoint_LZDao)getDao()).getLZNumByCodeExceptSelf(project.getProjectUuid(), surveyPoint.getMonitorItem().getMonitorItemUuid(), surveyPoint.getCodeChar(), surveyPoint.getCode(), surveyPoint.getSurveyPointUuid());
		if(num>0){
			throw new Exception("存在相同编号的测点");
		}
		surveyPoint.setProject(project);
		getDao().updateEntity(surveyPoint);
	}

	@Override
	public void deleteSP_LZ(String surveyPointUuid) {
		SurveyPoint_LZ sp=getDao().getEntity(surveyPointUuid);
		//如果有数据，则先要删除数据????????????????
		lzService.deleteLZDataBySP(surveyPointUuid);
		getDao().deleteEntity(sp);
	}


}
