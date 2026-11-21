import React, { useState, useEffect } from 'react';
import { 
  Bot, 
  ShieldCheck, 
  FileText, 
  Upload, 
  Terminal, 
  Search, 
  AlertTriangle, 
  CheckCircle2, 
  Clock, 
  Sparkles,
  Lock,
  RefreshCw,
  Trash2
} from 'lucide-react';
import { RagResponse, DocumentItem, AuditLogItem } from './types';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<'chat' | 'admin' | 'audit' | 'cli'>('chat');
  
  // Chat state
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(false);
  const [chatHistory, setChatHistory] = useState<Array<{
    query: string;
    response: RagResponse;
    timestamp: string;
  }>>([]);

  // Documents state
  const [documents, setDocuments] = useState<DocumentItem[]>([]);

  // Admin state
  const [adminKey, setAdminKey] = useState('adminkey123');
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadDesc, setUploadDesc] = useState('');
  const [uploadLoading, setUploadLoading] = useState(false);
  const [uploadMessage, setUploadMessage] = useState<{ text: string; type: 'success' | 'error' } | null>(null);

  // Raw text ingest state
  const [rawTitle, setRawTitle] = useState('');
  const [rawContent, setRawContent] = useState('');

  // Audit state
  const [auditLogs, setAuditLogs] = useState<AuditLogItem[]>([]);

  // Fetch documents on load
  useEffect(() => {
    fetchDocuments();
  }, []);

  const fetchDocuments = async () => {
    try {
      const res = await fetch('/api/documents');
      if (res.ok) {
        const data = await res.json();
        setDocuments(data);
      }
    } catch (err) {
      console.error('Error fetching documents:', err);
    }
  };

  const fetchAuditLogs = async () => {
    try {
      const res = await fetch('/api/admin/audit-logs', {
        headers: { 'X-Admin-Key': adminKey }
      });
      if (res.ok) {
        const data = await res.json();
        setAuditLogs(data.reverse());
      }
    } catch (err) {
      console.error('Error fetching audit logs:', err);
    }
  };

  const handleQuery = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim() || loading) return;

    setLoading(true);
    const userPrompt = query.trim();
    setQuery('');

    try {
      const res = await fetch('/api/query', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query: userPrompt, topK: 4, userRole: 'USER' })
      });
      const data: RagResponse = await res.json();

      setChatHistory(prev => [
        ...prev,
        {
          query: userPrompt,
          response: data,
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        }
      ]);
    } catch (err) {
      console.error('Query error:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleFileUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!uploadFile) return;

    setUploadLoading(true);
    setUploadMessage(null);

    const formData = new FormData();
    formData.append('file', uploadFile);
    formData.append('description', uploadDesc);
    formData.append('uploadedBy', 'admin');

    try {
      const res = await fetch('/api/admin/upload', {
        method: 'POST',
        headers: { 'X-Admin-Key': adminKey },
        body: formData
      });
      if (res.ok) {
        setUploadMessage({ text: 'Document ingested and indexed successfully!', type: 'success' });
        setUploadFile(null);
        setUploadDesc('');
        fetchDocuments();
      } else {
        const err = await res.json();
        setUploadMessage({ text: err.error || 'Failed to upload document', type: 'error' });
      }
    } catch (err: any) {
      setUploadMessage({ text: err.message || 'Upload error', type: 'error' });
    } finally {
      setUploadLoading(false);
    }
  };

  const handleRawIngest = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!rawTitle.trim() || !rawContent.trim()) return;

    setUploadLoading(true);
    try {
      const res = await fetch('/api/admin/ingest-text', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Admin-Key': adminKey
        },
        body: JSON.stringify({
          title: rawTitle,
          content: rawContent,
          description: 'Direct Knowledge Paste',
          uploadedBy: 'admin'
        })
      });
      if (res.ok) {
        setUploadMessage({ text: 'Knowledge snippet embedded and indexed!', type: 'success' });
        setRawTitle('');
        setRawContent('');
        fetchDocuments();
      }
    } catch (err: any) {
      setUploadMessage({ text: err.message, type: 'error' });
    } finally {
      setUploadLoading(false);
    }
  };

  const handleDeleteDocument = async (id: number) => {
    if (!confirm('Are you sure you want to delete this document from the knowledge base?')) return;
    try {
      const res = await fetch(`/api/admin/documents/${id}`, {
        method: 'DELETE',
        headers: { 'X-Admin-Key': adminKey }
      });
      if (res.ok) {
        fetchDocuments();
      }
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      {/* SIDEBAR NAVIGATION */}
      <div style={{
        width: '280px',
        backgroundColor: '#0f172a',
        borderRight: '1px solid #1e293b',
        display: 'flex',
        flexDirection: 'column',
        padding: '24px 16px',
        flexShrink: 0
      }}>
        {/* Brand Header */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '32px' }}>
          <div style={{
            background: 'linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%)',
            padding: '10px',
            borderRadius: '12px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center'
          }}>
            <Bot size={24} color="#ffffff" />
          </div>
          <div>
            <h1 style={{ fontSize: '18px', fontWeight: 700, margin: 0, letterSpacing: '-0.02em' }}>Enterprise RAG</h1>
            <span style={{ fontSize: '11px', color: '#10b981', display: 'flex', alignItems: 'center', gap: '4px' }}>
              <span style={{ width: '6px', height: '6px', borderRadius: '50%', backgroundColor: '#10b981' }}></span>
              Spring Boot + Guardrails
            </span>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', flex: 1 }}>
          <button
            onClick={() => setActiveTab('chat')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              padding: '12px 16px',
              borderRadius: '10px',
              border: 'none',
              backgroundColor: activeTab === 'chat' ? '#1e293b' : 'transparent',
              color: activeTab === 'chat' ? '#38bdf8' : '#94a3b8',
              fontWeight: 600,
              fontSize: '14px',
              textAlign: 'left'
            }}
          >
            <Search size={18} />
            Knowledge Search
          </button>

          <button
            onClick={() => { setActiveTab('admin'); fetchDocuments(); }}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              padding: '12px 16px',
              borderRadius: '10px',
              border: 'none',
              backgroundColor: activeTab === 'admin' ? '#1e293b' : 'transparent',
              color: activeTab === 'admin' ? '#38bdf8' : '#94a3b8',
              fontWeight: 600,
              fontSize: '14px',
              textAlign: 'left'
            }}
          >
            <Upload size={18} />
            Admin Ingestion ({documents.length})
          </button>

          <button
            onClick={() => { setActiveTab('audit'); fetchAuditLogs(); }}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              padding: '12px 16px',
              borderRadius: '10px',
              border: 'none',
              backgroundColor: activeTab === 'audit' ? '#1e293b' : 'transparent',
              color: activeTab === 'audit' ? '#38bdf8' : '#94a3b8',
              fontWeight: 600,
              fontSize: '14px',
              textAlign: 'left'
            }}
          >
            <ShieldCheck size={18} />
            Security & Audit
          </button>

          <button
            onClick={() => setActiveTab('cli')}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              padding: '12px 16px',
              borderRadius: '10px',
              border: 'none',
              backgroundColor: activeTab === 'cli' ? '#1e293b' : 'transparent',
              color: activeTab === 'cli' ? '#38bdf8' : '#94a3b8',
              fontWeight: 600,
              fontSize: '14px',
              textAlign: 'left'
            }}
          >
            <Terminal size={18} />
            Terminal CLI Tool
          </button>
        </div>

        {/* Security badge at bottom of sidebar */}
        <div style={{
          backgroundColor: '#1e293b',
          borderRadius: '12px',
          padding: '14px',
          fontSize: '12px',
          color: '#cbd5e1'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#10b981', fontWeight: 600, marginBottom: '4px' }}>
            <ShieldCheck size={16} /> Active Guardrails
          </div>
          <p style={{ margin: 0, fontSize: '11px', color: '#94a3b8' }}>
            Prompt injection blocked. PII (email, phone, SSN) auto-masked.
          </p>
        </div>
      </div>

      {/* MAIN VIEWPORT */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', backgroundColor: '#0b0f17', overflow: 'hidden' }}>
        
        {/* ========================================================= */}
        {/* VIEW 1: KNOWLEDGE SEARCH / RAG CHAT */}
        {/* ========================================================= */}
        {activeTab === 'chat' && (
          <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            {/* Top Bar */}
            <div style={{
              padding: '16px 28px',
              borderBottom: '1px solid #1e293b',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              backgroundColor: '#0f172a'
            }}>
              <div>
                <h2 style={{ fontSize: '16px', fontWeight: 600, margin: 0 }}>Grounded Enterprise Query</h2>
                <span style={{ fontSize: '12px', color: '#94a3b8' }}>Queries are strictly grounded in {documents.length} verified documents</span>
              </div>
              <div style={{ display: 'flex', gap: '12px' }}>
                <span style={{
                  padding: '4px 10px',
                  backgroundColor: '#1e293b',
                  borderRadius: '20px',
                  fontSize: '12px',
                  color: '#38bdf8',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px'
                }}>
                  <Sparkles size={14} /> Gemini 2.5 Flash / Hybrid Fallback
                </span>
              </div>
            </div>

            {/* Conversation Log */}
            <div style={{ flex: 1, overflowY: 'auto', padding: '28px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
              {chatHistory.length === 0 ? (
                <div style={{ margin: 'auto', textAlign: 'center', maxWidth: '540px' }}>
                  <div style={{
                    width: '64px',
                    height: '64px',
                    borderRadius: '20px',
                    backgroundColor: '#1e293b',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    margin: '0 auto 16px auto',
                    color: '#38bdf8'
                  }}>
                    <Bot size={32} />
                  </div>
                  <h3 style={{ fontSize: '20px', fontWeight: 600, marginBottom: '8px' }}>Ask anything regarding ingested enterprise documents</h3>
                  <p style={{ color: '#94a3b8', fontSize: '14px', lineHeight: 1.6, marginBottom: '24px' }}>
                    The backend parses PDF, DOCX, and texts into dense vector chunks with cosine similarity matching and security guardrails.
                  </p>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', justifyContent: 'center' }}>
                    {[
                      'What is the RAG architecture overview?',
                      'Explain the corporate security policy and RBAC',
                      'How are PII and prompt injection handled?'
                    ].map((example, i) => (
                      <button
                        key={i}
                        onClick={() => setQuery(example)}
                        style={{
                          backgroundColor: '#1e293b',
                          border: '1px solid #334155',
                          borderRadius: '8px',
                          padding: '8px 14px',
                          color: '#e2e8f0',
                          fontSize: '12px'
                        }}
                      >
                        "{example}"
                      </button>
                    ))}
                  </div>
                </div>
              ) : (
                chatHistory.map((item, idx) => (
                  <div key={idx} style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    {/* User Prompt */}
                    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                      <div style={{
                        maxWidth: '70%',
                        backgroundColor: '#2563eb',
                        color: '#ffffff',
                        padding: '12px 18px',
                        borderRadius: '16px 16px 4px 16px',
                        fontSize: '14px',
                        lineHeight: 1.5
                      }}>
                        {item.query}
                        <div style={{ fontSize: '10px', opacity: 0.8, textAlign: 'right', marginTop: '4px' }}>{item.timestamp}</div>
                      </div>
                    </div>

                    {/* AI / RAG Response */}
                    <div style={{ display: 'flex', gap: '12px', alignItems: 'flex-start' }}>
                      <div style={{
                        width: '36px',
                        height: '36px',
                        borderRadius: '10px',
                        backgroundColor: item.response.guardrailPassed ? '#1e293b' : '#7f1d1d',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        color: item.response.guardrailPassed ? '#38bdf8' : '#f87171',
                        flexShrink: 0
                      }}>
                        {item.response.guardrailPassed ? <Bot size={20} /> : <AlertTriangle size={20} />}
                      </div>

                      <div style={{
                        maxWidth: '80%',
                        backgroundColor: '#1e293b',
                        border: item.response.guardrailPassed ? '1px solid #334155' : '1px solid #dc2626',
                        padding: '16px 20px',
                        borderRadius: '4px 16px 16px 16px',
                        fontSize: '14px',
                        lineHeight: 1.6
                      }}>
                        {/* Status bar */}
                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '10px', fontSize: '11px', color: '#94a3b8' }}>
                          <span style={{ display: 'flex', alignItems: 'center', gap: '4px', color: item.response.guardrailPassed ? '#10b981' : '#f87171' }}>
                            {item.response.guardrailPassed ? <CheckCircle2 size={13} /> : <AlertTriangle size={13} />}
                            {item.response.guardrailPassed ? 'Guardrail Passed' : 'Security Violation'}
                          </span>
                          <span>•</span>
                          <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                            <Clock size={13} /> {item.response.executionTimeMs}ms
                          </span>
                        </div>

                        {/* Answer text */}
                        <div style={{ whiteSpace: 'pre-wrap', color: '#f8fafc' }}>
                          {item.response.answer}
                        </div>

                        {/* Citations section */}
                        {item.response.citations && item.response.citations.length > 0 && (
                          <div style={{ marginTop: '16px', borderTop: '1px solid #334155', paddingTop: '12px' }}>
                            <div style={{ fontSize: '12px', fontWeight: 600, color: '#38bdf8', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                              <FileText size={14} /> Grounded Sources &amp; Citations ({item.response.citations.length})
                            </div>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                              {item.response.citations.map((c, cIdx) => (
                                <div key={cIdx} style={{
                                  backgroundColor: '#0f172a',
                                  padding: '8px 12px',
                                  borderRadius: '6px',
                                  fontSize: '11px',
                                  borderLeft: '3px solid #38bdf8'
                                }}>
                                  <div style={{ fontWeight: 600, color: '#e2e8f0', display: 'flex', justifyContent: 'space-between' }}>
                                    <span>[{cIdx + 1}] {c.documentName} (chunk #{c.chunkIndex})</span>
                                    <span style={{ color: '#10b981' }}>Similarity: {(c.relevanceScore * 100).toFixed(1)}%</span>
                                  </div>
                                  <div style={{ color: '#94a3b8', marginTop: '3px' }}>"{c.snippet}"</div>
                                </div>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>

            {/* Input Bar */}
            <div style={{ padding: '20px 28px', borderTop: '1px solid #1e293b', backgroundColor: '#0f172a' }}>
              <form onSubmit={handleQuery} style={{ display: 'flex', gap: '12px' }}>
                <input
                  type="text"
                  value={query}
                  onChange={e => setQuery(e.target.value)}
                  placeholder="Ask a question about uploaded documents (guardrailed)..."
                  disabled={loading}
                  style={{
                    flex: 1,
                    backgroundColor: '#1e293b',
                    border: '1px solid #334155',
                    borderRadius: '12px',
                    padding: '14px 18px',
                    color: '#ffffff',
                    fontSize: '14px',
                    outline: 'none'
                  }}
                />
                <button
                  type="submit"
                  disabled={loading || !query.trim()}
                  style={{
                    backgroundColor: '#3b82f6',
                    color: '#ffffff',
                    border: 'none',
                    borderRadius: '12px',
                    padding: '0 24px',
                    fontWeight: 600,
                    fontSize: '14px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    opacity: loading || !query.trim() ? 0.6 : 1
                  }}
                >
                  {loading ? <RefreshCw className="animate-spin" size={18} /> : <Search size={18} />}
                  Ask
                </button>
              </form>
            </div>
          </div>
        )}

        {/* ========================================================= */}
        {/* VIEW 2: ADMIN DOCUMENT INGESTION */}
        {/* ========================================================= */}
        {activeTab === 'admin' && (
          <div style={{ padding: '32px', overflowY: 'auto', height: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
              <div>
                <h2 style={{ fontSize: '20px', fontWeight: 700, margin: '0 0 4px 0' }}>Admin Knowledge Management</h2>
                <span style={{ fontSize: '13px', color: '#94a3b8' }}>Upload files (PDF, DOCX, TXT) to split into vector embeddings</span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <Lock size={16} color="#94a3b8" />
                <input
                  type="password"
                  value={adminKey}
                  onChange={e => setAdminKey(e.target.value)}
                  placeholder="X-Admin-Key"
                  style={{
                    backgroundColor: '#1e293b',
                    border: '1px solid #334155',
                    borderRadius: '8px',
                    padding: '6px 12px',
                    color: '#ffffff',
                    fontSize: '12px'
                  }}
                />
              </div>
            </div>

            {/* Notification alert */}
            {uploadMessage && (
              <div style={{
                padding: '12px 16px',
                borderRadius: '8px',
                marginBottom: '20px',
                backgroundColor: uploadMessage.type === 'success' ? '#064e3b' : '#7f1d1d',
                color: uploadMessage.type === 'success' ? '#6ee7b7' : '#fca5a5',
                fontSize: '13px'
              }}>
                {uploadMessage.text}
              </div>
            )}

            {/* Ingestion cards */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '24px', marginBottom: '32px' }}>
              {/* File Upload Form */}
              <div style={{ backgroundColor: '#1e293b', padding: '24px', borderRadius: '12px', border: '1px solid #334155' }}>
                <h3 style={{ fontSize: '15px', fontWeight: 600, marginTop: 0, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <Upload size={18} color="#38bdf8" /> Upload Document File
                </h3>
                <form onSubmit={handleFileUpload} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '12px', color: '#94a3b8', marginBottom: '6px' }}>Select File (PDF, DOCX, TXT, MD)</label>
                    <input
                      type="file"
                      onChange={e => setUploadFile(e.target.files ? e.target.files[0] : null)}
                      accept=".pdf,.docx,.txt,.md,.json,.csv"
                      style={{ color: '#cbd5e1', fontSize: '13px' }}
                    />
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '12px', color: '#94a3b8', marginBottom: '6px' }}>Description</label>
                    <input
                      type="text"
                      value={uploadDesc}
                      onChange={e => setUploadDesc(e.target.value)}
                      placeholder="e.g. Employee Handbook 2026"
                      style={{
                        width: '100%',
                        backgroundColor: '#0f172a',
                        border: '1px solid #334155',
                        borderRadius: '8px',
                        padding: '10px',
                        color: '#ffffff',
                        fontSize: '13px'
                      }}
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={uploadLoading || !uploadFile}
                    style={{
                      backgroundColor: '#3b82f6',
                      color: '#ffffff',
                      border: 'none',
                      borderRadius: '8px',
                      padding: '10px 16px',
                      fontWeight: 600,
                      fontSize: '13px',
                      marginTop: '8px'
                    }}
                  >
                    {uploadLoading ? 'Chunking & Indexing...' : 'Ingest Document'}
                  </button>
                </form>
              </div>

              {/* Direct Text Paste */}
              <div style={{ backgroundColor: '#1e293b', padding: '24px', borderRadius: '12px', border: '1px solid #334155' }}>
                <h3 style={{ fontSize: '15px', fontWeight: 600, marginTop: 0, marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <FileText size={18} color="#34d399" /> Paste Raw Knowledge Text
                </h3>
                <form onSubmit={handleRawIngest} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                  <div>
                    <label style={{ display: 'block', fontSize: '12px', color: '#94a3b8', marginBottom: '6px' }}>Document Title</label>
                    <input
                      type="text"
                      value={rawTitle}
                      onChange={e => setRawTitle(e.target.value)}
                      placeholder="e.g. Q3 Earnings Report Summary"
                      style={{
                        width: '100%',
                        backgroundColor: '#0f172a',
                        border: '1px solid #334155',
                        borderRadius: '8px',
                        padding: '10px',
                        color: '#ffffff',
                        fontSize: '13px'
                      }}
                    />
                  </div>
                  <div>
                    <label style={{ display: 'block', fontSize: '12px', color: '#94a3b8', marginBottom: '6px' }}>Content</label>
                    <textarea
                      value={rawContent}
                      onChange={e => setRawContent(e.target.value)}
                      rows={3}
                      placeholder="Paste raw text to chunk and vectorize..."
                      style={{
                        width: '100%',
                        backgroundColor: '#0f172a',
                        border: '1px solid #334155',
                        borderRadius: '8px',
                        padding: '10px',
                        color: '#ffffff',
                        fontSize: '13px',
                        resize: 'vertical'
                      }}
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={uploadLoading || !rawTitle || !rawContent}
                    style={{
                      backgroundColor: '#10b981',
                      color: '#ffffff',
                      border: 'none',
                      borderRadius: '8px',
                      padding: '10px 16px',
                      fontWeight: 600,
                      fontSize: '13px'
                    }}
                  >
                    Index Raw Text
                  </button>
                </form>
              </div>
            </div>

            {/* Document Table */}
            <h3 style={{ fontSize: '16px', fontWeight: 600, marginBottom: '14px' }}>Indexed Knowledge Repository ({documents.length})</h3>
            <div style={{ backgroundColor: '#1e293b', borderRadius: '12px', border: '1px solid #334155', overflow: 'hidden' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
                <thead>
                  <tr style={{ backgroundColor: '#0f172a', borderBottom: '1px solid #334155', color: '#94a3b8' }}>
                    <th style={{ padding: '12px 16px' }}>ID</th>
                    <th style={{ padding: '12px 16px' }}>Document Name</th>
                    <th style={{ padding: '12px 16px' }}>Type</th>
                    <th style={{ padding: '12px 16px' }}>Chunks</th>
                    <th style={{ padding: '12px 16px' }}>Uploaded By</th>
                    <th style={{ padding: '12px 16px' }}>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {documents.map((doc) => (
                    <tr key={doc.id} style={{ borderBottom: '1px solid #233152' }}>
                      <td style={{ padding: '12px 16px', color: '#94a3b8' }}>#{doc.id}</td>
                      <td style={{ padding: '12px 16px', fontWeight: 600, color: '#f8fafc' }}>
                        {doc.filename}
                        <div style={{ fontSize: '11px', color: '#94a3b8', fontWeight: 400 }}>{doc.description}</div>
                      </td>
                      <td style={{ padding: '12px 16px', color: '#38bdf8' }}>{doc.contentType}</td>
                      <td style={{ padding: '12px 16px' }}>
                        <span style={{ backgroundColor: '#0f172a', padding: '3px 8px', borderRadius: '4px', fontSize: '11px', color: '#a7f3d0' }}>
                          {doc.chunkCount} vectors
                        </span>
                      </td>
                      <td style={{ padding: '12px 16px', color: '#cbd5e1' }}>{doc.uploadedBy}</td>
                      <td style={{ padding: '12px 16px' }}>
                        <button
                          onClick={() => handleDeleteDocument(doc.id)}
                          style={{
                            backgroundColor: 'transparent',
                            border: 'none',
                            color: '#f87171',
                            padding: '4px 8px',
                            borderRadius: '4px'
                          }}
                        >
                          <Trash2 size={16} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ========================================================= */}
        {/* VIEW 3: SECURITY & AUDIT LOGS */}
        {/* ========================================================= */}
        {activeTab === 'audit' && (
          <div style={{ padding: '32px', overflowY: 'auto', height: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px' }}>
              <div>
                <h2 style={{ fontSize: '20px', fontWeight: 700, margin: '0 0 4px 0' }}>Security Guardrail &amp; Compliance Audit</h2>
                <span style={{ fontSize: '13px', color: '#94a3b8' }}>Real-time logs of queries, latency, and blocked prompt injection attacks</span>
              </div>
              <button
                onClick={fetchAuditLogs}
                style={{
                  backgroundColor: '#1e293b',
                  border: '1px solid #334155',
                  color: '#e2e8f0',
                  borderRadius: '8px',
                  padding: '8px 14px',
                  fontSize: '12px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px'
                }}
              >
                <RefreshCw size={14} /> Refresh Logs
              </button>
            </div>

            <div style={{ backgroundColor: '#1e293b', borderRadius: '12px', border: '1px solid #334155', overflow: 'hidden' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '13px' }}>
                <thead>
                  <tr style={{ backgroundColor: '#0f172a', borderBottom: '1px solid #334155', color: '#94a3b8' }}>
                    <th style={{ padding: '12px 16px' }}>Timestamp</th>
                    <th style={{ padding: '12px 16px' }}>Status</th>
                    <th style={{ padding: '12px 16px' }}>User Role</th>
                    <th style={{ padding: '12px 16px' }}>Query</th>
                    <th style={{ padding: '12px 16px' }}>Latency</th>
                  </tr>
                </thead>
                <tbody>
                  {auditLogs.map((log) => (
                    <tr key={log.id} style={{ borderBottom: '1px solid #233152' }}>
                      <td style={{ padding: '12px 16px', color: '#94a3b8', fontSize: '11px' }}>{log.timestamp}</td>
                      <td style={{ padding: '12px 16px' }}>
                        {log.guardrailBlocked ? (
                          <span style={{ backgroundColor: '#450a0a', color: '#f87171', padding: '3px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 600 }}>
                            BLOCKED
                          </span>
                        ) : (
                          <span style={{ backgroundColor: '#064e3b', color: '#6ee7b7', padding: '3px 8px', borderRadius: '4px', fontSize: '11px', fontWeight: 600 }}>
                            PASSED
                          </span>
                        )}
                      </td>
                      <td style={{ padding: '12px 16px', color: '#cbd5e1' }}>{log.userRole}</td>
                      <td style={{ padding: '12px 16px', color: '#f8fafc' }}>
                        "{log.userQuery}"
                        {log.guardrailBlocked && (
                          <div style={{ fontSize: '11px', color: '#fca5a5', marginTop: '2px' }}>
                            Reason: {log.guardrailReason}
                          </div>
                        )}
                      </td>
                      <td style={{ padding: '12px 16px', color: '#94a3b8' }}>{log.responseTimeMs}ms</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* ========================================================= */}
        {/* VIEW 4: TERMINAL TOOL (CLI) */}
        {/* ========================================================= */}
        {activeTab === 'cli' && (
          <div style={{ padding: '32px', overflowY: 'auto', height: '100%' }}>
            <h2 style={{ fontSize: '20px', fontWeight: 700, margin: '0 0 4px 0' }}>Interactive Terminal CLI Tool</h2>
            <span style={{ fontSize: '13px', color: '#94a3b8' }}>Use the RAG platform directly from your shell or bash scripts</span>

            <div style={{
              backgroundColor: '#0f172a',
              border: '1px solid #334155',
              borderRadius: '12px',
              padding: '20px',
              marginTop: '20px',
              fontFamily: 'JetBrains Mono, monospace',
              fontSize: '13px'
            }}>
              <div style={{ color: '#10b981', marginBottom: '12px' }}># Option 1: Direct Python CLI wrapper (Instant execution)</div>
              <div style={{ color: '#f8fafc', marginBottom: '6px' }}>./cli/rag ask "What is the corporate security policy?"</div>
              <div style={{ color: '#f8fafc', marginBottom: '20px' }}>./cli/rag docs</div>

              <div style={{ color: '#10b981', marginBottom: '12px' }}># Option 2: Java Spring CLI Shell</div>
              <div style={{ color: '#f8fafc', marginBottom: '6px' }}>java -cp backend/target/rag-enterprise-backend-1.0.0.jar com.rag.enterprise.cli.RagCliTool</div>
            </div>
          </div>
        )}

      </div>
    </div>
  );
};

export default App;
