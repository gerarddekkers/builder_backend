import { useState, useEffect } from 'react';
import { CompetenceRow, AssessmentFormData, Category, competenceRowToPayload } from './types';
import { AssessmentBuildRequest, AssessmentBuildResponse, XmlPreviewResponse, TranslateRequest, TranslateResponse, CategorySearchResult, CompetenceSearchResult } from './types/api';

function App() {
  const [formData, setFormData] = useState<AssessmentFormData>({
    assessmentName: '',
    assessmentNameEn: '',
    assessmentDescription: '',
    assessmentDescriptionEn: '',
    assessmentInstruction: '',
    assessmentInstructionEn: '',
    competences: [],
  });

  const [competences, setCompetences] = useState<CompetenceRow[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [buildResponse, setBuildResponse] = useState<AssessmentBuildResponse | null>(null);
  const [xmlPreview, setXmlPreview] = useState<XmlPreviewResponse | null>(null);

  const addCompetence = () => {
    const newCompetence: CompetenceRow = {
      id: crypto.randomUUID(),
      name: '',
      nameEn: '',
      description: '',
      descriptionEn: '',
      category: '',
      categoryDescription: '',
      categoryDescriptionEn: '',
      isNew: true,
    };
    setCompetences([...competences, newCompetence]);
  };

  const removeCompetence = (id: string) => {
    setCompetences(competences.filter(comp => comp.id !== id));
  };

  const updateCompetence = (id: string, field: keyof CompetenceRow, value: string) => {
    setCompetences(competences.map(comp => 
      comp.id === id ? { ...comp, [field]: value } : comp
    ));
  };

  const handleFormChange = (field: keyof AssessmentFormData, value: string) => {
    setFormData(prev => ({ ...prev, [field]: value }));
  };

  const buildAssessment = async () => {
    setIsLoading(true);
    try {
      const requestData: AssessmentBuildRequest = {
        assessmentName: formData.assessmentName,
        assessmentNameEn: formData.assessmentNameEn,
        assessmentDescription: formData.assessmentDescription,
        assessmentDescriptionEn: formData.assessmentDescriptionEn,
        assessmentInstruction: formData.assessmentInstruction,
        assessmentInstructionEn: formData.assessmentInstructionEn,
        competences: competences.map(competenceRowToPayload),
      };

      const response = await fetch('/api/assessments/build', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestData),
      });

      const result: AssessmentBuildResponse = await response.json();
      setBuildResponse(result);
    } catch (error) {
      console.error('Error building assessment:', error);
      setBuildResponse({
        success: false,
        message: 'Failed to build assessment',
      });
    } finally {
      setIsLoading(false);
    }
  };

  const previewXml = async () => {
    setIsLoading(true);
    try {
      const requestData: AssessmentBuildRequest = {
        assessmentName: formData.assessmentName,
        assessmentNameEn: formData.assessmentNameEn,
        assessmentDescription: formData.assessmentDescription,
        assessmentDescriptionEn: formData.assessmentDescriptionEn,
        assessmentInstruction: formData.assessmentInstruction,
        assessmentInstructionEn: formData.assessmentInstructionEn,
        competences: competences.map(competenceRowToPayload),
      };

      const response = await fetch('/api/xml/preview', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestData),
      });

      const result: XmlPreviewResponse = await response.json();
      setXmlPreview(result);
    } catch (error) {
      console.error('Error previewing XML:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const translateTexts = async (sourceLanguage: string, targetLanguage: string, texts: string[]) => {
    try {
      const requestData: TranslateRequest = {
        sourceLanguage,
        targetLanguage,
        texts,
      };

      const response = await fetch('/api/translate', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestData),
      });

      const result: TranslateResponse = await response.json();
      return result;
    } catch (error) {
      console.error('Error translating texts:', error);
      return { translations: texts, error: 'Translation failed' };
    }
  };

  const searchCategories = async (query: string): Promise<CategorySearchResult[]> => {
    try {
      const response = await fetch(`/api/categories?query=${encodeURIComponent(query)}`);
      return await response.json();
    } catch (error) {
      console.error('Error searching categories:', error);
      return [];
    }
  };

  const searchCompetences = async (query: string): Promise<CompetenceSearchResult[]> => {
    try {
      const response = await fetch(`/api/competences?query=${encodeURIComponent(query)}`);
      return await response.json();
    } catch (error) {
      console.error('Error searching competences:', error);
      return [];
    }
  };

  return (
    <div className="app">
      <h1>Assessment Builder</h1>
      
      <div className="form-section">
        <h2>Assessment Details</h2>
        <div className="form-group">
          <label>Assessment Name (NL):</label>
          <input
            type="text"
            value={formData.assessmentName}
            onChange={(e) => handleFormChange('assessmentName', e.target.value)}
          />
        </div>
        <div className="form-group">
          <label>Assessment Name (EN):</label>
          <input
            type="text"
            value={formData.assessmentNameEn}
            onChange={(e) => handleFormChange('assessmentNameEn', e.target.value)}
          />
        </div>
        <div className="form-group">
          <label>Description (NL):</label>
          <textarea
            value={formData.assessmentDescription}
            onChange={(e) => handleFormChange('assessmentDescription', e.target.value)}
          />
        </div>
        <div className="form-group">
          <label>Description (EN):</label>
          <textarea
            value={formData.assessmentDescriptionEn}
            onChange={(e) => handleFormChange('assessmentDescriptionEn', e.target.value)}
          />
        </div>
        <div className="form-group">
          <label>Instruction (NL):</label>
          <textarea
            value={formData.assessmentInstruction}
            onChange={(e) => handleFormChange('assessmentInstruction', e.target.value)}
          />
        </div>
        <div className="form-group">
          <label>Instruction (EN):</label>
          <textarea
            value={formData.assessmentInstructionEn}
            onChange={(e) => handleFormChange('assessmentInstructionEn', e.target.value)}
          />
        </div>
      </div>

      <div className="competences-section">
        <h2>Competences</h2>
        <button onClick={addCompetence}>Add Competence</button>
        
        <div className="competences-table">
          {competences.map((competence) => (
            <div key={competence.id} className="competence-row">
              <input
                type="text"
                placeholder="Name (NL)"
                value={competence.name}
                onChange={(e) => updateCompetence(competence.id, 'name', e.target.value)}
              />
              <input
                type="text"
                placeholder="Name (EN)"
                value={competence.nameEn}
                onChange={(e) => updateCompetence(competence.id, 'nameEn', e.target.value)}
              />
              <input
                type="text"
                placeholder="Description (NL)"
                value={competence.description}
                onChange={(e) => updateCompetence(competence.id, 'description', e.target.value)}
              />
              <input
                type="text"
                placeholder="Description (EN)"
                value={competence.descriptionEn}
                onChange={(e) => updateCompetence(competence.id, 'descriptionEn', e.target.value)}
              />
              <input
                type="text"
                placeholder="Category"
                value={competence.category}
                onChange={(e) => updateCompetence(competence.id, 'category', e.target.value)}
              />
              <input
                type="text"
                placeholder="Category Description (NL)"
                value={competence.categoryDescription}
                onChange={(e) => updateCompetence(competence.id, 'categoryDescription', e.target.value)}
              />
              <input
                type="text"
                placeholder="Category Description (EN)"
                value={competence.categoryDescriptionEn}
                onChange={(e) => updateCompetence(competence.id, 'categoryDescriptionEn', e.target.value)}
              />
              <button onClick={() => removeCompetence(competence.id)}>Remove</button>
            </div>
          ))}
        </div>
      </div>

      <div className="actions-section">
        <button onClick={buildAssessment} disabled={isLoading}>
          {isLoading ? 'Building...' : 'Build Assessment'}
        </button>
        <button onClick={previewXml} disabled={isLoading}>
          {isLoading ? 'Generating...' : 'Preview XML'}
        </button>
      </div>

      {buildResponse && (
        <div className="response-section">
          <h3>Build Response</h3>
          <div className={buildResponse.success ? 'success' : 'error'}>
            {buildResponse.message}
          </div>
          {buildResponse.warnings && buildResponse.warnings.length > 0 && (
            <div className="warnings">
              <h4>Warnings:</h4>
              <ul>
                {buildResponse.warnings.map((warning, index) => (
                  <li key={index}>{warning}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}

      {xmlPreview && (
        <div className="xml-preview-section">
          <h3>XML Preview</h3>
          <div className="xml-content">
            <h4>Questionnaire XML:</h4>
            <pre>{xmlPreview.questionnaireXml}</pre>
            <h4>Report XML:</h4>
            <pre>{xmlPreview.reportXml}</pre>
          </div>
          {xmlPreview.warnings && xmlPreview.warnings.length > 0 && (
            <div className="warnings">
              <h4>Warnings:</h4>
              <ul>
                {xmlPreview.warnings.map((warning, index) => (
                  <li key={index}>{warning}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default App;
