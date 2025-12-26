package org.mql.ia.rag.model;

import lombok.Data;
import java.util.List;

@Data
public class QueryResponse {
    private String answer;
    private List<String> sources;
    private long responseTime;
    private double confidence;
    
    public QueryResponse() {
	}

	public QueryResponse(String answer, List<String> sources, long responseTime, double confidence) {
		super();
		this.answer = answer;
		this.sources = sources;
		this.responseTime = responseTime;
		this.confidence = confidence;
	}

	public String getAnswer() {
		return answer;
	}

	public void setAnswer(String answer) {
		this.answer = answer;
	}

	public List<String> getSources() {
		return sources;
	}

	public void setSources(List<String> sources) {
		this.sources = sources;
	}

	public long getResponseTime() {
		return responseTime;
	}

	public void setResponseTime(long responseTime) {
		this.responseTime = responseTime;
	}

	public double getConfidence() {
		return confidence;
	}

	public void setConfidence(double confidence) {
		this.confidence = confidence;
	}
    
    
}