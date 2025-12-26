package org.mql.ia.rag.model;

import lombok.Data;

@Data
public class QueryRequest {
    private String question;
    private int topK = 3;
    
    public QueryRequest() {
	}
    
	public QueryRequest(String question, int topK) {
		super();
		this.question = question;
		this.topK = topK;
	}

	public String getQuestion() {
		return question;
	}

	public void setQuestion(String question) {
		this.question = question;
	}

	public int getTopK() {
		return topK;
	}

	public void setTopK(int topK) {
		this.topK = topK;
	}
    
    
}