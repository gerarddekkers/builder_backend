import { CompetencePayload } from '../types';

export interface AssessmentBuildRequest {
  assessmentName: string;
  assessmentNameEn: string;
  assessmentDescription: string;
  assessmentDescriptionEn: string;
  assessmentInstruction: string;
  assessmentInstructionEn: string;
  competences: CompetencePayload[];
}

export interface AssessmentBuildResponse {
  success: boolean;
  message?: string;
  warnings?: string[];
}

export interface XmlPreviewResponse {
  questionnaireXml: string;
  reportXml: string;
  warnings?: string[];
}

export interface TranslateRequest {
  sourceLanguage: string;
  targetLanguage: string;
  texts: string[];
}

export interface TranslateResponse {
  translations: string[];
  error?: string;
}

export interface CategorySearchResult {
  id: number;
  name: string;
  nameEn: string;
}

export interface CompetenceSearchResult {
  id: number;
  name: string;
  nameEn: string;
}
