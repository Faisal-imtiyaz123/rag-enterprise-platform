export interface Citation {
  documentName: string;
  chunkIndex: number;
  relevanceScore: number;
  snippet: string;
}

export interface RagResponse {
  answer: string;
  guardrailPassed: boolean;
  guardrailViolation?: string;
  citations: Citation[];
  executionTimeMs: number;
}

export interface DocumentItem {
  id: number;
  filename: string;
  contentType: string;
  fileSize: number;
  description: string;
  uploadedBy: string;
  chunkCount: number;
  createdAt: string;
}

export interface AuditLogItem {
  id: number;
  userRole: string;
  userQuery: string;
  guardrailBlocked: boolean;
  guardrailReason?: string;
  responseTimeMs: number;
  timestamp: string;
}
