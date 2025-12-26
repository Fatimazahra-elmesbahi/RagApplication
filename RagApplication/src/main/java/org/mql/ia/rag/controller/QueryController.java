package org.mql.ia.rag.controller;

import org.mql.ia.rag.model.QueryRequest;
import org.mql.ia.rag.model.QueryResponse;
import org.mql.ia.rag.service.RAGService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/query")
public class QueryController {

	private final RAGService ragService;

	public QueryController(RAGService ragService) {
		this.ragService = ragService;
	}

	@PostMapping
	public ResponseEntity<?> query(@RequestBody QueryRequest request) {
		try {
			QueryResponse response = ragService.processQuery(request);
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			return ResponseEntity.badRequest()
					.body(Map.of("error", e.getMessage()));
		}
	}
}