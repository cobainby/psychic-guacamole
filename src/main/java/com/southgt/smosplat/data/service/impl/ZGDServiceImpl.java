package com.southgt.smosplat.data.service.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.southgt.smosplat.common.dao.IBaseDao;
import com.southgt.smosplat.common.service.impl.BaseServiceImpl;
import com.southgt.smosplat.common.util.ApplicationUtil;
import com.southgt.smosplat.common.util.FileUtil;
import com.southgt.smosplat.common.util.JsonUtil;
import com.southgt.smosplat.data.dao.IZGDDataDao;
import com.southgt.smosplat.data.entity.ZGD_Data;
import com.southgt.smosplat.data.entity.WYD_LevelData;
import com.southgt.smosplat.data.service.IZGDService;
import com.southgt.smosplat.data.util.math.GtMath;
import com.southgt.smosplat.project.entity.MonitorItem;
import com.southgt.smosplat.project.entity.Project;
import com.southgt.smosplat.project.entity.SurveyPoint_ZGD;
import com.southgt.smosplat.project.entity.Warning;
import com.southgt.smosplat.project.service.IMonitorItemService;
import com.southgt.smosplat.project.service.IProjectService;
import com.southgt.smosplat.project.service.ISurveyPoint_ZGDService;
import com.southgt.smosplat.project.service.IWarnningDataService;
/**
 * 
 * 周边管线竖向位移数据服务实现类
 * @version v1.0.0
 * Copyright (C) 2017 广州南方高铁测绘技术有限公司 All rights reserved.
 * @author 姚家俊
 * <p>Modification History:</p>
 * <p> Date         Author      Version     Description</p>
 * <p>-----------------------------------------------------------------</p>
 * <p>2017年5月12日     姚家俊       v1.0.0        create</p>
 *
 */
@Service("zgdService")
public class ZGDServiceImpl extends BaseServiceImpl<ZGD_Data> implements IZGDService {

	@Value("#{uploadProperties['uploadFileSrc']}")
	private  String uploadFileSrc;
	@Resource
	ISurveyPoint_ZGDService surveyPointZGDService;
	
	@Resource
	IMonitorItemService monitorItemService;
	
	@Resource
	IWarnningDataService warningDataService;
	
	@Resource
	IProjectService projectService;
	@Resource(name="zgdDataDao")
	@Override
	public void setDao(IBaseDao<ZGD_Data> dao) {
		super.setDao(dao);
	}
	@Override
	public Map<String, Object> checkZGDData(String jsonOriginalData,String sourceData) throws Exception {
		Map<String,Object>map =new HashMap();
		ObjectMapper mapper = new ObjectMapper();
		SurveyPoint_ZGD surveyPoint = null;
		
		//超过报警值部分
		List<String> gapRateWarningPoints = new ArrayList<>();
		List<Double> gapRateWarningOffset = new ArrayList<>();
		List<Float>  gapRateWarningVal = new ArrayList<>();

		//超过报警值部分
		List<String> accumWarningPoints = new ArrayList<>();
		List<Double> accumWarningOffset = new ArrayList<>();
		List<Float>  accumWarningVal = new ArrayList<>();
		//超过控制值部分
		List<String> accumControlWarningPoints = new ArrayList<>();
		List<Double> accumControlWarningOffset = new ArrayList<>();
		List<Float>  accumControlWarningVal = new ArrayList<>();
		//超限的点上一条数据
		List<Double> lastTimeOffset = new ArrayList<>();
		//超限的点第一条数据
		List<Double> firstTimeOffset = new ArrayList<>();
		//本次测量（计算值）
		Map<String, Object> currentValMap = new HashMap();
		//所有解算坐标点的监测点Uuid的List
		List<ZGD_Data> dataList = new ArrayList<>();
		//是否有点没有数据
		boolean isExtraData = false;
		//通过树节点解析json数据
		JsonNode root=null;
		try {
			root = mapper.readTree(jsonOriginalData);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		String projectUuid = root.get("projectUuid").asText();
		String monitorItemUuid = root.get("monitorItemUuid").asText();
		String deviceUuid = root.get("deviceUuid").asText();
		Date d ;
		//根据项目uuid，监测项uuid获取所有的监测点
		List<SurveyPoint_ZGD> lstSurveyPoint = surveyPointZGDService.getSP_ZGDs(projectUuid, monitorItemUuid);
		Map<String, Object> spFirstThreeData = new HashMap();
		String surveyTime = "";
		try{
			//去除空值
			lstSurveyPoint.removeAll(Collections.singleton(null));
			if(lstSurveyPoint.size()==0){
				throw(new Exception("请先在后台添加监测点！"));
			}
			
			for (int i = 0; i < lstSurveyPoint.size(); i++) {
				String spUuid = lstSurveyPoint.get(i).getSurveyPointUuid();
					
				List<String> spId = new ArrayList<String>();
				spId.add(spUuid);
				double aveH = 0.0;
				//头三条数据
				List<ZGD_Data> firstThreeData = ((IZGDDataDao)getDao()).getFirstThreeDataBySurveyPoint(spId);
				if(firstThreeData.size() > 0){
					for(int kk = 0; kk < firstThreeData.size(); kk ++){
						aveH += firstThreeData.get(kk).getLevelH();
					}
					//把头三条数据的第一条的东北高赋成头三条东北高的平均值。
					firstThreeData.get(0).setLevelH(aveH / firstThreeData.size());
					spFirstThreeData.put(spUuid, firstThreeData.get(0));
				}
			}
			
			
			
			JsonNode originalData = root.get("originalData");
			//测量时间(作原始文件名)
			surveyTime = originalData.get("surveyDateTime").asText();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
			d = sdf.parse(surveyTime);
			SimpleDateFormat sdf1 = new SimpleDateFormat("yyyyMMddHHmmss");
			String millionTime = sdf1.format(d);
			//水准测站
			JsonNode stations = originalData.get("stations");
			
			//是否附和导线测量。1为附合导线，0为闭合导线。
			int type = originalData.get("type").asInt();
			//控制点点名。若是闭合导线测量，则头跟尾是同一个点；若是附和导线测量，则不是。
			String hname = originalData.get("hname").asText();
			//闭合点高程
			double hvalue = originalData.get("hvalue").asDouble();
			//最后一个控制点点名
			String lastControlPointName = "";
			//最后一个控制点高程
			double lastControlPointHeight = 0.0;
			if(type == 1){
				lastControlPointName = originalData.get("lastControlPointName").asText();
				lastControlPointHeight = originalData.get("lastControlPointHeight").asDouble();
		    	//附合导线，把第一个控制点存起来
		    	List<SurveyPoint_ZGD> tempList = lstSurveyPoint.stream().filter(p->p.getCode().equals(hname)).collect(Collectors.toList());
		    	if(tempList != null && tempList.size()>0){
		    		surveyPoint = tempList.get(0);
		    		ZGD_Data item = new ZGD_Data();
		    		item.setLevelH(hvalue);
		    		item.setSurveyPoint(surveyPoint);
		    		item.setSurveyTime(d);
//		    		this.getDao().saveEntity(item);
		    		dataList.add(item);
		    	}
			}
			//站数
			int stationCount =0;
			//累计高差
			double lastPointHeight = 0.0;
			String fName = "";
			if (stations.isArray()) {
				//高程累积差
			    for (Iterator it =  stations.elements(); it.hasNext(); ){
			    	JsonNode s = (JsonNode) it.next();
			    	//前视点高程
			    	double fHeight = s.get("fheight").asDouble();
			    	lastPointHeight = fHeight;
			    	//前视点名字
			    	fName = s.get("fname").asText();
			    	String n = fName;
			    	//从后台得到的监测点列表匹配点名。匹配不上的，可能是点名错误，也可能是转点，不保存。
			    	List<SurveyPoint_ZGD> tempList = lstSurveyPoint.stream().filter(p->p.getCode().equals(n)).collect(Collectors.toList());
			    	if(tempList != null && tempList.size()>0){
			    		//转点不算站
			    		stationCount++;
			    		surveyPoint = tempList.get(0);
			    		ZGD_Data item = new ZGD_Data();
			    		item.setLevelH(fHeight);
			    		item.setSurveyPoint(surveyPoint);
			    		item.setSurveyTime(d);
	//		    		this.getDao().saveEntity(item);
			    		dataList.add(item);
			    	}
			    }
			    //要不要控制高程累计差？
	//		    if(heightDiff>2等水准规范，4*Math.sqar(L))
			}
			//高程改正数。平差用。
			double delH = 0.0;
			if(type == 0){
				if(!fName.equals(hname)){
					throw(new Exception("没有闭合到控制点上！请检查导线类型与数据是否匹配！"));
				}
				//闭合导线
				delH = (hvalue - lastPointHeight)/stationCount;
			}else if(type == 1){
				//此时fName是最后一个点的点名。没有附合到控制点上。
				if(!fName.equals(lastControlPointName)){
					throw(new Exception("没有附合到控制点上！请检查导线类型与数据是否匹配！"));
				}else{
					//附合导线，站数比点数小1
					delH = (lastControlPointHeight - lastPointHeight)/(stationCount - 1);
				}
			}
			if(dataList.size() == 0){
				throw(new Exception("全部点匹配不上，入库失败，请检查！"));
			}else if(dataList.size()<lstSurveyPoint.size()){
				isExtraData = true;
				//后台建好的点当中有的点没有数据
				for(int i = 0; i<dataList.size(); i++){
					String code = dataList.get(i).getSurveyPoint().getCode();
					List<SurveyPoint_ZGD> s = lstSurveyPoint.stream().filter(p->p.getCode().equals(code)).collect(Collectors.toList());
					if(s != null && s.size()>0){
						lstSurveyPoint.remove(s.get(0));
					}
				}
			}
			double tempDelH = delH;
			for(int i = 0; i < dataList.size(); i++){
				if(type == 1){
					//每个点都加上高程改正数。头一个不加，因为是起算点（附合路线）
					dataList.get(i).setLevelH(Double.parseDouble(new java.text.DecimalFormat("#.0000").format(dataList.get(i).getLevelH() + i*tempDelH)));
				}else if(type == 0){
					//每个点都加上高程改正数。闭合路线，这里的基准点放在了列表最后一位。所以平差从第一个点开始。
					dataList.get(i).setLevelH(Double.parseDouble(new java.text.DecimalFormat("#.0000").format(dataList.get(i).getLevelH() + (i + 1)*tempDelH)));
				}
				String sid = dataList.get(i).getSurveyPoint().getSurveyPointUuid();
				Warning warn = dataList.get(i).getSurveyPoint().getWarning();
				//点名和本次测量值的键值对
				currentValMap.put(dataList.get(i).getSurveyPoint().getCode(), dataList.get(i).getLevelH());
				List<String> spId = new ArrayList<>();
				spId.add(sid);
				//一个点的最新11条数据
				List<ZGD_Data> latest = ((IZGDDataDao)getDao()).getLatestZGD_DataBySurveyPoint(spId);
	    		if(latest !=null && latest.size()>0){
	    			//化为毫米
	    			double gapHOffset = (dataList.get(i).getLevelH() - latest.get(latest.size() - 1).getLevelH())*1000;
	    			
	    			String dd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(dataList.get(i).getSurveyTime());
					long today = GtMath.fromDateStringToLong(dd);
					String dd1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(latest.get(latest.size() - 1).getSurveyTime());
					long lastTime = GtMath.fromDateStringToLong(dd1);
					double days = ((today - lastTime)/(1000*60*60*24));
					BigDecimal bb = new BigDecimal(days); 
					days = bb.setScale(0,   BigDecimal.ROUND_UP).doubleValue();  
					if(days == 0.0){
						days = 1.0;
					}
					//单次变化速率
					double gapHChangeRate = gapHOffset/days;
					// 保留1位小数
					String b = new java.text.DecimalFormat("#.0").format(gapHChangeRate);
					gapHChangeRate = Double.parseDouble(b);
					dataList.get(i).setGapHChangeRate(gapHChangeRate);
	    			// 高程单次位移
					//保留1位小数
					b = new java.text.DecimalFormat("#.0").format(gapHOffset);
					gapHOffset = Double.parseDouble(b);
					dataList.get(i).setGapHOffset(gapHOffset);
					
	    			if(Math.abs(gapHChangeRate) > Math.abs(warn.getWarnSingleRate())){
	    				//超过了单次变化速率报警值
	    				gapRateWarningPoints.add(dataList.get(i).getSurveyPoint().getCode());
	    				gapRateWarningOffset.add(gapHChangeRate);
	    				gapRateWarningVal.add(warn.getWarnSingleRate());
	    				lastTimeOffset.add(latest.get(latest.size() - 1).getLevelH());
	    			}
	    		}
	    		//找出前三条测出的均值
	    		ZGD_Data first = (ZGD_Data) spFirstThreeData.get(sid);
	    		if(first != null){
	    			//化为毫米
	    			double accumHOffset = (dataList.get(i).getLevelH() - first.getLevelH())*1000 + dataList.get(i).getSurveyPoint().getOriginalTotalValue();
	    			//保留1位小数
					String b = new java.text.DecimalFormat("#.0").format(accumHOffset);
					accumHOffset = Double.parseDouble(b);
	    			dataList.get(i).setGapHOffset(accumHOffset);
	    			dataList.get(i).setAccumHOffset(accumHOffset);
	    			
	    			if(Math.abs(accumHOffset) > Math.abs(warn.getWarnAccum())){
	    				//超过了累计变化报警值
	    				accumWarningPoints.add(dataList.get(i).getSurveyPoint().getCode());
	    				accumWarningOffset.add(accumHOffset);
	    				accumWarningVal.add(warn.getWarnAccum());
	    				firstTimeOffset.add(first.getLevelH());
	    			}
	    			if(Math.abs(accumHOffset) > Math.abs(warn.getControlAccum())){
	    				//超过了累计变化控制值
	    				accumControlWarningPoints.add(dataList.get(i).getSurveyPoint().getCode());
	    				accumControlWarningOffset.add(accumHOffset);
	    				accumControlWarningVal.add(warn.getControlAccum());
	    			}
	    		}
			}
			
			List<String> extraPointCode = new ArrayList<>();
			if(isExtraData == true){
				//有的点后台建了但是传过来的数据中没有，提示到前台
				for(int i = 0; i<lstSurveyPoint.size(); i++){
					extraPointCode.add(lstSurveyPoint.get(i).getCode());
				}
			}
			//保存原始数据
			String organUuid = dataList.get(0).getSurveyPoint().getProject().getOrgan().getOrganUuid();
			FileUtil.saveSourceFile(organUuid, projectUuid, uploadFileSrc, millionTime,"ZGD", sourceData);
			//本次测量值
			map.put("currentVal",currentValMap);
			//监测点列表
			map.put("surveyPointList", lstSurveyPoint);
			//数据
			map.put("data", dataList);
			//后台没建点的点号
			map.put("extraPointCode", extraPointCode);
			//单次变化速率超报警值的点号
			map.put("gapRateEarlyWarningPoints", gapRateWarningPoints);
			//该超限点上一次测量的值
			map.put("lastTimeOffset", lastTimeOffset);
			//超单次变化速率的点的单次变化速率列表
			map.put("gapRateWarningOffset", gapRateWarningOffset);
			//单次变化报警值
			map.put("gapRateWarningVal", gapRateWarningVal);
			//超累计变化报警值的点名列表
			map.put("accumWarningPoints", accumWarningPoints);
			//超累计变化报警值点的累计变化量
			map.put("accumWarningOffset", accumWarningOffset);
			//累计变化量报警值
			map.put("accumWarningVal", accumWarningVal);
			//超累计变化量控制值的点名列表
			map.put("accumControlWarningPoints", accumControlWarningPoints);
			//超累计变化量控制值点的累计变化量列表
			map.put("accumControlWarningOffset", accumControlWarningOffset);
			//累计变化量控制值
			map.put("accumControlWarningVal", accumControlWarningVal);
			//该超限点第一次测量的值
			map.put("firstTimeOffset", firstTimeOffset);
		}catch(org.hibernate.exception.SQLGrammarException e){
			throw(new Exception("数据库操作失败！请检查数据"));
		}
		
		return map;
	}

	@Override
	public Map<String, Object> getLatestZGDDatasByProject(Project project) {
		Map<String, Object> result=new HashMap<String, Object>();
		Map<String, Object> dataResult = new HashMap<String, Object>();
		//围护墙(边坡)顶部竖向位移
		MonitorItem monitorItem = monitorItemService.getMonitorItemByNumber(10);
		// 首先获取对应项目下对应监测项的所有监测点
		List<SurveyPoint_ZGD> sps;
		//每一个点的第一个时间点的数据
		Map<String, ZGD_Data> spFirstThreeData = new HashMap<String, ZGD_Data>();
		try{
			sps = surveyPointZGDService.getSP_ZGDs(project.getProjectUuid(),monitorItem.getMonitorItemUuid());
			for(int i = 0; i < sps.size(); i++){
				dataResult.put(sps.get(i).getCode(), new ArrayList<WYD_LevelData>());
			}
			result.put("surveyPoints", sps);
			//数据库没有点
			if(sps.size() == 0){
	//			throw(new Exception("数据库没有点！"));
				result.put("dataList",dataResult);
				return result;
			}
			for (int i = 0; i < sps.size(); i++) {
				String spUuid = sps.get(i).getSurveyPointUuid();
					
				List<String> spId = new ArrayList<String>();
				spId.add(spUuid);
				double aveH = 0.0;
				//头三条数据
				List<ZGD_Data> firstThreeData = ((IZGDDataDao)getDao()).getFirstThreeDataBySurveyPoint(spId);
				if(firstThreeData.size() > 0){
					for(int kk = 0; kk < firstThreeData.size(); kk ++){
						aveH += firstThreeData.get(kk).getLevelH();
					}
					//把头三条数据的第一条的东北高赋成头三条东北高的平均值。
					firstThreeData.get(0).setLevelH(aveH / firstThreeData.size());
					spFirstThreeData.put(sps.get(i).getCode(), firstThreeData.get(0));
				}
			}
		}catch(Exception ex){
			result.put("levelData", dataResult);
			return result;
		}
		List<Integer> noDataIndex = new ArrayList<>();
		for (int i = 0; i < sps.size(); i++) {
			ZGD_Data temp = (ZGD_Data) spFirstThreeData.get(sps.get(i).getCode());
			if(temp ==null){
				//这个点没有数据
				noDataIndex.add(i);
			}
		}
		//计算累计位移
		for (int i = 0; i < sps.size(); i++) {
			int t = i;
			List<Integer> noData = noDataIndex.stream().filter(p->p.equals(t)).collect(Collectors.toList());
			//这个点没有数据
			if(noData!=null && noData.size()>0){
				continue;
			}
			List<String> spId = new ArrayList<String>();
			spId.add(sps.get(i).getSurveyPointUuid());
			//一个点的最新11条数据
			List<ZGD_Data> dataAtOnePoint = ((IZGDDataDao)getDao()).getLatestZGD_DataBySurveyPoint(spId);
			//只在有数据的情况下才进行计算
			if (dataAtOnePoint!=null && dataAtOnePoint.size() > 0) {
				for (int j = 0; j < dataAtOnePoint.size(); j++) {
					if (j < dataAtOnePoint.size() - 1) {
						// 高程单次位移
						double gapHOffset = (dataAtOnePoint.get(j + 1).getLevelH() - dataAtOnePoint.get(j).getLevelH())*1000;
						String dd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(dataAtOnePoint.get(j + 1).getSurveyTime());
						long today = GtMath.fromDateStringToLong(dd);
						String dd1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(dataAtOnePoint.get(j).getSurveyTime());
						long lastTime = GtMath.fromDateStringToLong(dd1);
						double days = ((today - lastTime)/(1000*60*60*24));
						BigDecimal bb = new BigDecimal(days); 
						//取整数位，进1法。1.1->2,0.9->1;
						days = bb.setScale(0,   BigDecimal.ROUND_UP).doubleValue();  
						if(days == 0.0){
							days = 1.0;
						}
						//单次变化速率
						double gapHChangeRate = gapHOffset/days;
						// 保留1位小数
						String b = new java.text.DecimalFormat("#.0").format(gapHChangeRate);
						gapHChangeRate = Double.parseDouble(b);
						//保留1位小数
						b = new java.text.DecimalFormat("#.0").format(gapHOffset);
						gapHOffset = Double.parseDouble(b);
						// 高程累计位移
						double accumHOffset = (dataAtOnePoint.get(j + 1).getLevelH() - spFirstThreeData.get(sps.get(i).getCode()).getLevelH())*1000 + sps.get(i).getOriginalTotalValue();
						b = new java.text.DecimalFormat("#.0").format(accumHOffset);
						accumHOffset = Double.parseDouble(b);
						dataAtOnePoint.get(j + 1).setAccumHOffset(accumHOffset);
						dataAtOnePoint.get(j + 1).setGapHOffset(gapHOffset);
						dataAtOnePoint.get(j + 1).setGapHChangeRate(gapHChangeRate);
						double h = (dataAtOnePoint.get(j + 1).getLevelH());
						b = new java.text.DecimalFormat("#.0000").format(h);
						h = Double.parseDouble(b);
						dataAtOnePoint.get(j + 1).setLevelH(h);
					} else if(dataAtOnePoint.size()>10){
						dataAtOnePoint.remove(0);
					}
				}
				dataResult.put(sps.get(i).getCode(), dataAtOnePoint);
			}else{
				dataResult.put(sps.get(i).getCode(), new ArrayList<WYD_LevelData>());
			}
		}
		result.put("levelData", dataResult);
		return result;
	}
	@Override
	public void deleteZGDDataBySurveyPoint(String spUuid) {
		((IZGDDataDao)getDao()).deleteZGDDataBySurveyPoint(spUuid);
		
	}
	@Override
	public void saveManualData(Project project,List<ZGD_Data> data, List<String> gapRateEarlyWarningPoints, List<String> accumEarlyWarningPoints, byte flag) throws Exception {
		try{
			//可上传的数据
			List<ZGD_Data> acceptedData = new ArrayList<>();
			if(flag == 0){
				//只上传不超限部分
				for(int i = 0; i< data.size(); i++){
					String pn = data.get(i).getSurveyPoint().getCode();
					List<String> overrunChangeRatePoints = gapRateEarlyWarningPoints.stream().filter(p -> p.equals(pn)).collect(Collectors.toList());
					List<String> overrunAccumPoints = accumEarlyWarningPoints.stream().filter(p -> p.equals(pn)).collect(Collectors.toList());
					if(overrunChangeRatePoints.size() > 0 || overrunAccumPoints.size() > 0){
						continue;
					}
					//原始数据入库
					this.saveEntity(data.get(i));
					acceptedData.add(data.get(i));
				}
			}else if(flag == 1){
				//全部上传
				for(int i = 0; i< data.size(); i++){
					//原始数据入库
					this.saveEntity(data.get(i));
					acceptedData.add(data.get(i));
				}
			}
			
			//把最新上传的一次数据推送到前台
			String msg = JsonUtil.beanToJson(acceptedData);
			System.out.println(msg+"\r\n");
			ApplicationUtil.getSimpMessagingTemplate().convertAndSend("/collect/projectZGDCollect/"+project.getProjectUuid(), msg);
			//发短信给项目监测人员和监测负责人。
			String phones = "";
			String contacts = project.getMonitorWorker();
			contacts += project.getMonitorLeader();
			while(contacts.contains("(")||contacts.contains(")")){
				String temp = contacts.substring(contacts.indexOf("(")+1,contacts.indexOf(")"));
				if(!phones.contains(temp)){
					phones += temp+",";
				}
				contacts = contacts.substring(contacts.indexOf(")")+1);
			}
			//计算是否超限并更新工程状态。
			warningDataService.calWarnningOffsetByMonitorItem("ZGD", project,phones);
		}catch(org.hibernate.exception.SQLGrammarException e){
			throw(new Exception("数据库操作失败！请检查数据"));
		}
		
	}

}
