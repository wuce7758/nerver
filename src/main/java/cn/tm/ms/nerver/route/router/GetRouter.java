package cn.tm.ms.nerver.route.router;

import java.io.InputStream;
import java.net.URLDecoder;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import cn.tm.ms.nerver.common.utils.ByteUtils;
import cn.tm.ms.nerver.common.utils.StringUtils;
import cn.tm.ms.nerver.route.entry.NerverRequest;
import cn.tm.ms.nerver.route.entry.NerverRespone;
import cn.tm.ms.nerver.route.loadbalance.RandLoadBalance;
import cn.tm.ms.nerver.route.support.RouteRegexRule;
import cn.tm.ms.nerver.route.type.CodeMsg;

/**
 * GET类型路由器
 * @author lry
 */
public class GetRouter {
	
	/**
	 * GET请求路由
	 * @param routeRules 路由规则列表
	 * @param pathInfo 请求path信息
	 * @param request
	 * @return
	 * @throws Exception
	 */
	public NerverRespone route(Map<String,String[]> routeRules,String pathInfo,NerverRequest request) throws Exception {
		NerverRespone nerverRespone=new NerverRespone();
		CloseableHttpClient httpClient=null;
		CloseableHttpResponse httpResponse=null;
		InputStream inputStream=null;
		try {
			//负载获取路由地址
			String[] urls=new RouteRegexRule().regex(pathInfo, routeRules);
			if(urls==null||urls.length<1){
				nerverRespone.setStatusCode(HttpServletResponse.SC_NOT_FOUND);
				nerverRespone.setData("No source server address can be routed.".getBytes());
				return nerverRespone;
			}
			
			//路由
			String routeUrl=new RandLoadBalance().loadBalance(urls);
			if(StringUtils.isNotBlank(routeUrl)){
				//读取参数
				String queryString = request.getQueryString();
				if (queryString == null || queryString.equals("")) {
					queryString="";
				}
				String encoding=request.getEncoding();
				if(encoding==null||encoding.equals("")){
					encoding="UTF-8";
				}
				String decodedQueryString = URLDecoder.decode(queryString, encoding);
				
				//创建请求
				httpClient = HttpClients.createDefault();
				HttpGet http = new HttpGet(routeUrl+"?"+decodedQueryString);
				
				//设置请求头
				for (Map.Entry<String, String> entry:request.getHeaders().entrySet()) {
					http.setHeader(entry.getKey(), entry.getValue());
				}
				
				//通信
				httpResponse = httpClient.execute(http);
				
				//设置路由响应头
				Header[] headers=httpResponse.getAllHeaders();
				if(headers!=null){
					for (Header header:headers) {
						nerverRespone.addHeader("route_"+header.getName(), header.getValue());
					}
				}
				
				//返回状态码 ，用来进行识别或者判断访问结果
				int statusCode = httpResponse.getStatusLine().getStatusCode();
				if(statusCode == HttpServletResponse.SC_OK){//执行成功
					//解析结果
					inputStream=httpResponse.getEntity().getContent();
					if(inputStream==null){
						nerverRespone.setData("".getBytes());
					}else{
						//结果转化
						byte[] data=ByteUtils.inputStream2byte(inputStream);
						if(data==null){
							nerverRespone.setData("".getBytes());
						}else{
							nerverRespone.setData(data);
						}
					}
				}else{
					nerverRespone.setData(("Route is failure, error: "+CodeMsg.getMsg(statusCode)).getBytes());
				}
				nerverRespone.setStatusCode(statusCode);
			}else{//没有可用的路由列表
				nerverRespone.setStatusCode( HttpServletResponse.SC_NOT_FOUND);
				nerverRespone.setData("Route loadBalance is failure.".getBytes());
			}
		} catch (Throwable t) {
			throw new RuntimeException(t);
		} finally {
			try {
				if(inputStream!=null){
					inputStream.close();
				}
				if(httpResponse!=null){
					httpResponse.close();
				}
				if(httpClient!=null){
					httpClient.close();
				}
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
		return nerverRespone;
	}
	
}
