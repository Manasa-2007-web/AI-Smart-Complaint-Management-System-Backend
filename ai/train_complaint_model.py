import json
from collections import Counter
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    confusion_matrix,
    precision_recall_fscore_support,
)
from sklearn.model_selection import StratifiedKFold

ROOT = Path(__file__).resolve().parents[1]
DATASET_PATH = ROOT / "ai" / "data" / "complaint_training_data.csv"
MODEL_DIR = ROOT / "models"

CATEGORY_MODEL_PATH = MODEL_DIR / "category_model.joblib"
PRIORITY_MODEL_PATH = MODEL_DIR / "priority_model.joblib"
VECTORIZER_PATH = MODEL_DIR / "vectorizer.joblib"

EXPECTED_CATEGORIES = {
    "ACADEMIC",
    "BILLING",
    "SERVICE",
    "SECURITY",
    "FACILITY",
    "TECHNICAL",
    "OTHER",
}
EXPECTED_PRIORITIES = {"LOW", "MEDIUM", "HIGH", "URGENT"}
RANDOM_STATE = 42
MAX_FOLDS = 5


def build_text(row):
    title = "" if pd.isna(row["title"]) else str(row["title"]).strip()
    description = "" if pd.isna(row["description"]) else str(row["description"]).strip()
    return " ".join(part for part in (title, description) if part)


def load_dataset():
    if not DATASET_PATH.is_file():
        raise FileNotFoundError(f"Missing training dataset: {DATASET_PATH}")

    data = pd.read_csv(DATASET_PATH)
    required_columns = {"title", "description", "category", "priority"}
    missing_columns = required_columns - set(data.columns)
    if missing_columns:
        raise ValueError(f"Missing required columns: {sorted(missing_columns)}")
    if data.empty:
        raise ValueError("The training dataset contains no examples.")

    data["category"] = data["category"].fillna("").astype(str).str.strip().str.upper()
    data["priority"] = data["priority"].fillna("").astype(str).str.strip().str.upper()
    data["text"] = data.apply(build_text, axis=1)

    empty_text_rows = (data["text"].str.len() == 0).to_numpy().nonzero()[0]
    if len(empty_text_rows):
        csv_rows = (empty_text_rows + 2).tolist()
        raise ValueError(f"Title and description are both empty on CSV row(s): {csv_rows}")

    empty_labels = data["category"].eq("") | data["priority"].eq("")
    if empty_labels.any():
        csv_rows = (empty_labels[empty_labels].index + 2).tolist()
        raise ValueError(f"Category or priority is empty on CSV row(s): {csv_rows}")

    categories = set(data["category"])
    priorities = set(data["priority"])
    if categories != EXPECTED_CATEGORIES:
        raise ValueError(
            f"Expected categories {sorted(EXPECTED_CATEGORIES)}, found {sorted(categories)}"
        )
    if priorities != EXPECTED_PRIORITIES:
        raise ValueError(
            f"Expected priorities {sorted(EXPECTED_PRIORITIES)}, found {sorted(priorities)}"
        )

    normalized_text = data["text"].str.casefold().str.split().str.join(" ")
    duplicate_mask = normalized_text.duplicated(keep=False)
    if duplicate_mask.any():
        duplicate_rows = (data.index[duplicate_mask] + 2).tolist()
        raise ValueError(f"Duplicate normalized complaint text on CSV row(s): {duplicate_rows}")

    return data


def make_classifier():
    return LogisticRegression(
        max_iter=3000,
        solver="lbfgs",
        random_state=RANDOM_STATE,
    )


def make_vectorizer():
    return TfidfVectorizer(
        ngram_range=(1, 2),
        min_df=1,
        stop_words=None,
    )


def evaluate_target(texts, labels, target_name, class_names):
    label_counts = Counter(labels)
    smallest_class = min(label_counts.values())
    if smallest_class < 2:
        raise ValueError(
            f"{target_name} evaluation needs at least two examples per class; "
            f"smallest class has {smallest_class}."
        )

    fold_count = min(MAX_FOLDS, smallest_class)
    splitter = StratifiedKFold(
        n_splits=fold_count,
        shuffle=True,
        random_state=RANDOM_STATE,
    )
    out_of_fold_predictions = [None] * len(texts)
    fold_metrics = {
        "accuracy": [],
        "macro_precision": [],
        "macro_recall": [],
        "macro_f1": [],
        "weighted_f1": [],
    }

    for train_indices, test_indices in splitter.split(texts, labels):
        train_texts = [texts[index] for index in train_indices]
        test_texts = [texts[index] for index in test_indices]
        train_labels = [labels[index] for index in train_indices]
        test_labels = [labels[index] for index in test_indices]

        vectorizer = make_vectorizer()
        train_vectors = vectorizer.fit_transform(train_texts)
        test_vectors = vectorizer.transform(test_texts)

        model = make_classifier()
        model.fit(train_vectors, train_labels)
        predictions = model.predict(test_vectors)
        for index, prediction in zip(test_indices, predictions):
            out_of_fold_predictions[index] = prediction

        fold_metrics["accuracy"].append(accuracy_score(test_labels, predictions))
        macro_precision, macro_recall, macro_f1, _ = precision_recall_fscore_support(
            test_labels,
            predictions,
            labels=class_names,
            average="macro",
            zero_division=0,
        )
        _, _, weighted_f1, _ = precision_recall_fscore_support(
            test_labels,
            predictions,
            labels=class_names,
            average="weighted",
            zero_division=0,
        )
        fold_metrics["macro_precision"].append(macro_precision)
        fold_metrics["macro_recall"].append(macro_recall)
        fold_metrics["macro_f1"].append(macro_f1)
        fold_metrics["weighted_f1"].append(weighted_f1)

    if any(prediction is None for prediction in out_of_fold_predictions):
        raise RuntimeError(f"{target_name} cross-validation did not predict every example.")

    print(f"\n{target_name} evaluation ({fold_count}-fold stratified cross-validation)")
    print("Fold metric mean +/- standard deviation:")
    for metric_name, values in fold_metrics.items():
        print(f"  {metric_name}: {np.mean(values):.4f} +/- {np.std(values):.4f}")

    print("Out-of-fold per-class metrics and aggregate report:")
    print(classification_report(
        labels,
        out_of_fold_predictions,
        labels=class_names,
        target_names=class_names,
        digits=3,
        zero_division=0,
    ))
    matrix = confusion_matrix(labels, out_of_fold_predictions, labels=class_names)
    print("Out-of-fold confusion matrix (actual rows, predicted columns):")
    print(pd.DataFrame(matrix, index=class_names, columns=class_names).to_string())


def main():
    data = load_dataset()
    texts = data["text"].tolist()
    category_labels = data["category"].tolist()
    priority_labels = data["priority"].tolist()
    categories = sorted(EXPECTED_CATEGORIES)
    priorities = sorted(EXPECTED_PRIORITIES)

    print("Dataset (synthetic development examples; not real user complaints)")
    print(f"  Total examples: {len(data)}")
    print("  Category distribution:")
    for label, count in sorted(Counter(category_labels).items()):
        print(f"    {label}: {count}")
    print("  Priority distribution:")
    for label, count in sorted(Counter(priority_labels).items()):
        print(f"    {label}: {count}")

    evaluate_target(texts, category_labels, "Category", categories)
    evaluate_target(texts, priority_labels, "Priority", priorities)

    deployment_vectorizer = make_vectorizer()
    all_vectors = deployment_vectorizer.fit_transform(texts)

    category_model = make_classifier()
    category_model.fit(all_vectors, category_labels)

    priority_model = make_classifier()
    priority_model.fit(all_vectors, priority_labels)

    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    joblib.dump(deployment_vectorizer, VECTORIZER_PATH)
    joblib.dump(category_model, CATEGORY_MODEL_PATH)
    joblib.dump(priority_model, PRIORITY_MODEL_PATH)

    print("\nDeployment model trained on full dataset.")
    print("Saved:")
    print(f"  {VECTORIZER_PATH.name}")
    print(f"  {CATEGORY_MODEL_PATH.name}")
    print(f"  {PRIORITY_MODEL_PATH.name}")


if __name__ == "__main__":
    main()
