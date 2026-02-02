export interface Competence {
  id: string;
  name: string;
  nameEn: string;
  description: string;
  descriptionEn: string;
  category: string;
  categoryDescription: string;
  categoryDescriptionEn: string;
  isNew?: boolean;
}

// UI Model - for React state and components
export interface CompetenceRow {
  id: string; // client-generated UUID for React keys
  name: string;
  nameEn: string;
  description: string;
  descriptionEn: string;
  category: string;
  categoryDescription: string;
  categoryDescriptionEn: string;
  isNew?: boolean;
}

// API Payload - for backend requests (no client IDs)
export interface CompetencePayload {
  name: string;
  nameEn: string;
  description: string;
  descriptionEn: string;
  category: string;
  categoryDescription: string;
  categoryDescriptionEn: string;
}

export interface Category {
  id: number;
  name: string;
  nameEn: string;
}

export interface AssessmentFormData {
  assessmentName: string;
  assessmentNameEn: string;
  assessmentDescription: string;
  assessmentDescriptionEn: string;
  assessmentInstruction: string;
  assessmentInstructionEn: string;
  competences: Competence[];
}

// Mapping function: UI model → API payload
export function competenceRowToPayload(row: CompetenceRow): CompetencePayload {
  return {
    name: row.name,
    nameEn: row.nameEn,
    description: row.description,
    descriptionEn: row.descriptionEn,
    category: row.category,
    categoryDescription: row.categoryDescription,
    categoryDescriptionEn: row.categoryDescriptionEn,
  };
}
