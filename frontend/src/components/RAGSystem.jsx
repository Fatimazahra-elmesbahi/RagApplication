import React, { useState, useEffect, useCallback } from "react";
import {
  Upload, Send, FileText, Database, Zap, MessageSquare,
  ThumbsUp, ThumbsDown, Trash2, Settings, PlayCircle,
  LogOut, User, AlertCircle, Loader
} from "lucide-react";
import axios from "axios";
import "./RaGSystem.css";

const RAGSystem = ({ user, onLogout }) => {
  const [documents, setDocuments] = useState([]);
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("");
  const [isProcessing, setIsProcessing] = useState(false);
  const [systemStatus, setSystemStatus] = useState("ready");
  const [feedback, setFeedback] = useState({});
  const [error, setError] = useState(null);
  const [uploadProgress, setUploadProgress] = useState({});
  const [stats, setStats] = useState({
    totalDocs: 0,
    totalQueries: 0,
    avgResponseTime: 0,
    positiveRate: 0,
  });

  const API_URL = "http://localhost:8080/api";
  const token = localStorage.getItem("token");

  const axiosConfig = {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  };

  useEffect(() => {
    loadUserDocuments();
  }, []);

  const loadUserDocuments = async () => {
    try {
      const response = await axios.get(`${API_URL}/documents`, axiosConfig);
      setDocuments(response.data);
      setStats(prev => ({ ...prev, totalDocs: response.data.length }));
    } catch (error) {
      console.error("Erreur chargement documents:", error);
    }
  };

  const handleFileUpload = async (event) => {
    const files = Array.from(event.target.files);
    if (files.length === 0) return;

    setIsProcessing(true);
    setSystemStatus("processing");
    setError(null);

    const uploadPromises = files.map(async (file) => {
      if (file.size > 10 * 1024 * 1024) {
        throw new Error(`${file.name}: fichier trop volumineux (max 10 MB)`);
      }

      if (!file.name.endsWith('.txt')) {
        throw new Error(`${file.name}: seuls les fichiers .txt sont acceptés`);
      }

      const formData = new FormData();
      formData.append("file", file);

      setUploadProgress(prev => ({ ...prev, [file.name]: 0 }));

      try {
        const response = await axios.post(
          `${API_URL}/documents/upload-langchain`,
          formData,
          {
            ...axiosConfig,
            headers: {
              ...axiosConfig.headers,
              "Content-Type": "multipart/form-data",
              "Authorization": `Bearer ${localStorage.getItem("token")}`
            },
            onUploadProgress: (progressEvent) => {
              const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
              setUploadProgress(prev => ({ ...prev, [file.name]: percent }));
            },
          }
        );

        setUploadProgress(prev => {
          const newProgress = { ...prev };
          delete newProgress[file.name];
          return newProgress;
        });

        return {
          id: response.data.id,
          name: response.data.filename,
          chunkCount: response.data.chunkCount,
          uploadedAt: response.data.uploadedAt,
        };
      } catch (error) {
        setUploadProgress(prev => {
          const newProgress = { ...prev };
          delete newProgress[file.name];
          return newProgress;
        });
        throw new Error(`${file.name}: ${error.response?.data?.error || error.message}`);
      }
    });

    try {
      const uploadedDocs = await Promise.allSettled(uploadPromises);
      
      const successful = uploadedDocs
        .filter(result => result.status === 'fulfilled')
        .map(result => result.value);
      
      const failed = uploadedDocs
        .filter(result => result.status === 'rejected')
        .map(result => result.reason.message);

      if (successful.length > 0) {
        setDocuments(prev => [...prev, ...successful]);
        setStats(prev => ({
          ...prev,
          totalDocs: prev.totalDocs + successful.length,
        }));
      }

      if (failed.length > 0) {
        setError(`Erreurs d'upload:\n${failed.join('\n')}`);
      }
    } catch (error) {
      setError("Erreur lors de l'upload des fichiers");
    }

    setIsProcessing(false);
    setSystemStatus("ready");
  };

  const handleQuery = useCallback(async () => {
    if (!input.trim() || documents.length === 0 || isProcessing) return;

    const userMessage = { 
      role: "user", 
      content: input, 
      timestamp: Date.now() 
    };
    
    setMessages(prev => [...prev, userMessage]);
    setInput("");
    setIsProcessing(true);
    setSystemStatus("retrieving");
    setError(null);

    const startTime = Date.now();

    try {
      setSystemStatus("generating");

      const response = await axios.post(
        `${API_URL}/query-langchain`,
        { question: input, topK: 3 },
        axiosConfig
      );

      const aiMessage = {
        role: "assistant",
        content: response.data.answer,
        timestamp: Date.now(),
        sources: response.data.sources || [],
        responseTime: response.data.responseTime,
        confidence: response.data.confidence,
        chunksUsed: response.data.chunksUsed,
      };

      setMessages(prev => [...prev, aiMessage]);

      const totalTime = Date.now() - startTime;
      setStats(prev => ({
        ...prev,
        totalQueries: prev.totalQueries + 1,
        avgResponseTime: Math.round(
          (prev.avgResponseTime * prev.totalQueries + totalTime) / (prev.totalQueries + 1)
        ),
      }));
    } catch (error) {
      console.error("Erreur RAG:", error);
      setError(error.response?.data?.error || "Erreur lors de la génération de la réponse");
      
      setMessages(prev => [
        ...prev,
        {
          role: "assistant",
          content: "Désolé, une erreur est survenue lors du traitement de votre question.",
          timestamp: Date.now(),
          error: true,
        },
      ]);
    }

    setIsProcessing(false);
    setSystemStatus("ready");
  }, [input, documents, isProcessing]);

  const handleFeedback = (messageId, type) => {
    setFeedback(prev => ({ ...prev, [messageId]: type }));

    const allFeedback = { ...feedback, [messageId]: type };
    const positive = Object.values(allFeedback).filter(f => f === "positive").length;
    const total = Object.keys(allFeedback).length;

    setStats(prev => ({
      ...prev,
      positiveRate: total > 0 ? Math.round((positive / total) * 100) : 0,
    }));
  };

  const handleDeleteDocument = async (docId) => {
    try {
      await axios.delete(`${API_URL}/documents/${docId}`, axiosConfig);
      setDocuments(prev => prev.filter(doc => doc.id !== docId));
      setStats(prev => ({ ...prev, totalDocs: prev.totalDocs - 1 }));
    } catch (error) {
      setError("Erreur lors de la suppression du document");
    }
  };

  const clearChat = () => {
    setMessages([]);
    setFeedback({});
  };

  const clearDocuments = async () => {
    if (!window.confirm("Êtes-vous sûr de vouloir supprimer tous les documents ?")) {
      return;
    }
    
    try {
      await Promise.all(documents.map(doc => 
        axios.delete(`${API_URL}/documents/${doc.id}`, axiosConfig)
      ));
      setDocuments([]);
      setStats(prev => ({ ...prev, totalDocs: 0 }));
    } catch (error) {
      setError("Erreur lors de la suppression des documents");
    }
  };

  return (
    <div className="rag-system-container">
      <div className="rag-header">
        <div className="header-content">
          <div className="header-flex">
            <div className="header-left">
              <Zap />
              <div>
                <h1 className="header-title">Système RAG Local</h1>
                <p className="header-subtitle">Retrieval-Augmented Generation</p>
              </div>
            </div>
            <div className="header-right">
              <div className="user-info">
                <User />
                <span className="username">{user.username}</span>
              </div>
              <div className={`status-badge status-${systemStatus}`}>
                {systemStatus === 'ready' && '🟢 Prêt'}
                {systemStatus === 'processing' && '🟡 Traitement...'}
                {systemStatus === 'retrieving' && '🔵 Recherche...'}
                {systemStatus === 'generating' && '🟣 Génération...'}
              </div>
              <button onClick={onLogout} className="logout-btn">
                <LogOut />
                Déconnexion
              </button>
            </div>
          </div>
        </div>
      </div>

      {error && (
        <div className="error-banner">
          <div className="error-flex">
            <AlertCircle />
            <div>
              <p className="error-title">Erreur</p>
              <p className="error-text">{error}</p>
            </div>
            <button onClick={() => setError(null)} className="error-close">✕</button>
          </div>
        </div>
      )}

      <div className="main-content">
        <div className="grid-layout">
          <div className="sidebar">
            <div className="card">
              <div className="card-header">
                <Database />
                <h2 className="card-title">Base de Documents</h2>
              </div>

              <label className={`upload-label ${isProcessing ? 'disabled' : ''}`}>
                <div className={`upload-zone ${isProcessing ? 'processing' : ''}`}>
                  {isProcessing ? (
                    <Loader className="loader-spin" />
                  ) : (
                    <Upload />
                  )}
                  <p className="upload-text">Glissez vos documents ici</p>
                  <p className="upload-subtext">ou cliquez pour parcourir (.txt, max 10 MB)</p>
                </div>
                <input
                  type="file"
                  multiple
                  accept=".txt"
                  onChange={handleFileUpload}
                  disabled={isProcessing}
                  className="file-input"
                />
              </label>

              {Object.keys(uploadProgress).length > 0 && (
                <div className="upload-progress">
                  {Object.entries(uploadProgress).map(([filename, progress]) => (
                    <div key={filename} className="progress-item">
                      <div className="progress-header">
                        <span>{filename}</span>
                        <span>{progress}%</span>
                      </div>
                      <div className="progress-bar">
                        <div className="progress-fill" style={{ width: `${progress}%` }} />
                      </div>
                    </div>
                  ))}
                </div>
              )}

              <div className="document-list-container">
                {documents.map((doc) => (
                  <div key={doc.id} className="document-item">
                    <FileText />
                    <div className="document-info">
                      <p className="document-name">{doc.name}</p>
                      <p className="document-chunks">{doc.chunkCount} chunks</p>
                    </div>
                    <button onClick={() => handleDeleteDocument(doc.id)} className="delete-btn">
                      <Trash2 />
                    </button>
                  </div>
                ))}
              </div>

              {documents.length > 0 && (
                <button onClick={clearDocuments} className="clear-documents-btn">
                  <Trash2 />
                  Effacer tous les documents
                </button>
              )}
            </div>

            <div className="stats-card">
              <div className="stats-header">
                <Settings />
                <h2 className="stats-title">Métriques</h2>
              </div>

              <div className="stats-grid">
                <div className="stat-item">
                  <p className="stat-label">Documents indexés</p>
                  <p className="stat-value stat-docs">{stats.totalDocs}</p>
                </div>
                <div className="stat-item">
                  <p className="stat-label">Requêtes traitées</p>
                  <p className="stat-value stat-queries">{stats.totalQueries}</p>
                </div>
                <div className="stat-item">
                  <p className="stat-label">Temps de réponse moyen</p>
                  <p className="stat-value stat-time">{stats.avgResponseTime}ms</p>
                </div>
                <div className="stat-item">
                  <p className="stat-label">Taux de satisfaction</p>
                  <p className="stat-value stat-satisfaction">{stats.positiveRate}%</p>
                </div>
              </div>
            </div>
          </div>

          <div className="chat-panel">
            <div className="chat-header">
              <div className="chat-header-left">
                <MessageSquare />
                <h2 className="chat-title">Assistant RAG</h2>
              </div>
              {messages.length > 0 && (
                <button onClick={clearChat} className="clear-chat-btn">
                  <Trash2 />
                  Effacer
                </button>
              )}
            </div>

            <div className="messages-container">
              {messages.length === 0 ? (
                <div className="empty-state">
                  <PlayCircle className="empty-icon" />
                  <p className="empty-title">Aucune conversation</p>
                  <p className="empty-subtitle">Uploadez des documents et posez vos questions</p>
                </div>
              ) : (
                messages.map((msg, idx) => (
                  <div key={idx} className={`message-row ${msg.role === 'user' ? 'message-row-user' : 'message-row-assistant'}`}>
                    <div className={`message-bubble message-${msg.role} ${msg.error ? 'message-error' : ''}`}>
                      <p className="message-content">{msg.content}</p>

                      {msg.role === 'assistant' && msg.sources && msg.sources.length > 0 && (
                        <div className="message-sources">
                          <p className="sources-label">Sources:</p>
                          <div className="sources-tags">
                            {[...new Set(msg.sources)].map((source, i) => (
                              <span key={i} className="source-tag">{source}</span>
                            ))}
                          </div>
                          {msg.responseTime && (
                            <p className="response-time">
                              {msg.responseTime}ms • Confiance: {msg.confidence?.toFixed(0)}%
                            </p>
                          )}
                        </div>
                      )}

                      {msg.role === 'assistant' && !msg.error && (
                        <div className="feedback-buttons">
                          <button
                            onClick={() => handleFeedback(msg.timestamp, 'positive')}
                            className={`feedback-btn ${feedback[msg.timestamp] === 'positive' ? 'feedback-positive' : ''}`}
                          >
                            <ThumbsUp className="feedback-icon" />
                          </button>
                          <button
                            onClick={() => handleFeedback(msg.timestamp, 'negative')}
                            className={`feedback-btn ${feedback[msg.timestamp] === 'negative' ? 'feedback-negative' : ''}`}
                          >
                            <ThumbsDown className="feedback-icon" />
                          </button>
                        </div>
                      )}
                    </div>
                  </div>
                ))
              )}
            </div>

            <div className="input-container">
              <div className="input-flex">
                <input
                  type="text"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyPress={(e) => e.key === 'Enter' && !e.shiftKey && handleQuery()}
                  placeholder={
                    documents.length === 0
                      ? "Uploadez d'abord des documents..."
                      : "Posez votre question..."
                  }
                  disabled={documents.length === 0 || isProcessing}
                  className="message-input"
                />
                <button
                  onClick={handleQuery}
                  disabled={!input.trim() || documents.length === 0 || isProcessing}
                  className="send-btn"
                >
                  <Send className="send-icon" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default RAGSystem;