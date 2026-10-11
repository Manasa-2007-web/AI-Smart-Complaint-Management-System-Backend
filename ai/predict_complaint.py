import argparse
import json
from pathlib import Path

import joblib

ROOT = Path(__file__).resolve().parents[1]
MODEL_DIR = ROOT / 'models'
VECTORIZER_PATH = MODEL_DIR / 'vectorizer.joblib'
CATEGORY_MODEL_PATH = MODEL_DIR / 'category_model.joblib'
PRIORITY_MODEL_PATH = MODEL_DIR / 'priority_model.joblib'


def load_models():
    if not VECTORIZER_PATH.exists():
        raise FileNotFoundError(f'Missing vectorizer model: {VECTORIZER_PATH}')
    if not CATEGORY_MODEL_PATH.exists():
        raise FileNotFoundError(f'Missing category model: {CATEGORY_MODEL_PATH}')
    if not PRIORITY_MODEL_PATH.exists():
        raise FileNotFoundError(f'Missing priority model: {PRIORITY_MODEL_PATH}')

    vectorizer = joblib.load(VECTORIZER_PATH)
    category_model = joblib.load(CATEGORY_MODEL_PATH)
    priority_model = joblib.load(PRIORITY_MODEL_PATH)
    return vectorizer, category_model, priority_model


def predict(title: str, description: str):
    vectorizer, category_model, priority_model = load_models()
    text = ' '.join(part for part in [title, description] if part and str(part).strip())
    if not text.strip():
        raise ValueError('Title and description cannot both be empty.')

    vector = vectorizer.transform([text])
    category = category_model.predict(vector)[0]
    priority = priority_model.predict(vector)[0]

    category_prob = max(category_model.predict_proba(vector)[0])
    priority_prob = max(priority_model.predict_proba(vector)[0])
    confidence = round(float(max(category_prob, priority_prob)), 4)

    return {
        'category': str(category).upper(),
        'priority': str(priority).upper(),
        'confidence': confidence,
    }


def main():
    parser = argparse.ArgumentParser(description='Predict complaint category and priority from a complaint title and description.')
    parser.add_argument('--title', default='', help='Complaint title')
    parser.add_argument('--description', default='', help='Complaint description')
    args = parser.parse_args()

    result = predict(args.title, args.description)
    print(json.dumps(result))


if __name__ == '__main__':
    main()
