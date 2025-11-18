# Documentation du Workflow de Mutation Testing

## Vue d'ensemble

Ce workflow GitHub Actions implémente un système de **mutation testing** automatisé avec validation de régression. Il compare automatiquement les scores de mutation entre le commit actuel et le commit précédent, échouant la build si une régression est détectée.

---

## Architecture du Workflow

### 1. Configuration de base

```yaml
on: push
strategy:
    fail-fast: false
    matrix:
        java-version: [21, 24]
```

Le workflow s'exécute sur deux versions de Java :

-   **Java 21** : Ajoutée spécifiquement pour correspondre à l'environnement de développement local et obtenir des scores cohérents
-   **Java 24** : Version plus récente pour validation de compatibilité

Le mode `fail-fast: false` permet aux deux builds de s'exécuter indépendamment.

---

## Étapes du Workflow

### Phase 1 : Build et Mutation Testing (Commit Actuel)

#### Étape 1.1 : Checkout et Setup

```yaml
- uses: actions/checkout@v4
  with:
      fetch-depth: 2 # Récupère les 2 derniers commits pour comparaison
```

#### Étape 1.2 : Mise en cache

Trois niveaux de cache pour optimiser les temps de build :

-   **Maven artifacts** (`~/.m2/repository`)
-   **Node** (`web-bundle/node`)
-   **Node modules** (`web-bundle/node_modules`)

#### Étape 1.3 : Build initial

```bash
mvn -B clean install -DskipTests
```

Compile le projet sans exécuter les tests pour préparer le mutation testing.

#### Étape 1.4 : Exécution de Pitest

```bash
mvn org.pitest:pitest-maven:mutationCoverage \
  -pl core,navigation,client-hc,example,reader-gtfs,web-bundle,web-api,web
```

**Configuration :**

-   Timeout : 120 minutes
-   Mémoire : 6GB heap, 2GB initial, 1GB metaspace
-   Modules testés : 8 modules du projet

#### Étape 1.5 : Extraction des scores actuels

Un script bash sophistiqué parcourt les rapports HTML générés par Pitest :

```bash
extract_score() {
  local module=$1
  local REPORT_DIR=$(find $module/target/pit-reports -type d -mindepth 1 -maxdepth 1 2>/dev/null | head -1)

  if [ -n "$REPORT_DIR" ] && [ -f "$REPORT_DIR/index.html" ]; then
    local SCORE=$(grep -A 10 -m 1 "Mutation Coverage" "$REPORT_DIR/index.html" | \
                  grep -oP '(?<=<td>)\d+(?=% <div class="coverage_bar")' | sed -n '2p')
    echo "$SCORE"
  else
    echo "error"
  fi
}
```

**Fonctionnement :**

1. Localise le répertoire de rapport dans `target/pit-reports`
2. Parse le fichier `index.html` avec grep et regex
3. Extrait le pourcentage de mutation coverage
4. Stocke les résultats dans `$GITHUB_OUTPUT` pour usage ultérieur

---

### Phase 2 : Build et Mutation Testing (Commit Précédent)

#### Étape 2.1 : Checkout du commit précédent

```yaml
if: github.event.before != '0000000000000000000000000000000000000000'
```

**Condition :** Ne s'exécute que si un commit précédent existe (pas le premier commit).

```bash
git checkout ${{ github.event.before }}
```

#### Étape 2.2-2.4 : Build et mutation testing identiques

Les mêmes étapes sont répétées pour le commit précédent avec l'option `continue-on-error: true` pour éviter que le workflow ne s'arrête en cas d'échec sur l'ancien code.

---

### Phase 3 : Comparaison et Validation

#### Logique de comparaison

```bash
compare_module() {
  local module_name=$1
  local current=$2
  local previous=$3

  # Validation des scores
  if [ "$previous" != "" ] && [ "$previous" != "N/A" ] && \
     [ "$previous" != "error" ] && [ "$previous" != "unknown" ] && \
     [ "$current" != "error" ] && [ "$current" != "unknown" ]; then

    DIFF=$((current - previous))

    if [ $DIFF -lt 0 ]; then
      echo "::error::$module_name mutation score decreased from $previous% to $current% ($DIFF%)"
      return 1  # Échec
    fi
  fi

  return 0
}
```

**Résultats possibles :**

-   ✅ **Amélioration** : Score actuel > Score précédent → `+X%`
-   ⚠️ **Régression** : Score actuel < Score précédent → `-X%` → **BUILD FAILS**
-   ➡️ **Stable** : Score actuel = Score précédent → `No change`
-   ℹ️ **N/A** : Comparaison impossible (premier commit, erreur d'extraction, etc.)

---

## Affichage des Résultats

### Format de sortie console

```
======================================
 MUTATION SCORE COMPARISON
======================================
 Java Version: 21
 System: Linux

────────────────────────────────────
Module: CORE
────────────────────────────────────
Previous: 25%
Current:  25%
 Result: No change

────────────────────────────────────
Module: NAVIGATION
────────────────────────────────────
Previous: 60%
Current:  59%
 Result: Regression (-1%)
::error::NAVIGATION mutation score decreased from 60% to 59% (-1%)

======================================
❌ BUILD FAILED: One or more modules had mutation score regression
```

## Test de Validation

### Méthode de test

Pour vérifier le bon fonctionnement du workflow, décommenter ces deux méthodes dans `com.graphhopper.util.ArrayUtil` :

```java
// Décommenter ces méthodes pour tester la détection de régression
public int multiply(int a, int b) {
    if (a == 0 || b == 0) {
        return 0;
    }
    return a * b;
}

public static boolean isEven(int number) {
    return number % 2 == 0;
}
```

### Impact attendu

**Effet sur le mutation score :**

1. **Augmentation du code** : Plus de code = plus de mutations possibles
2. **Aucun test associé** : Les mutations ne sont pas détectées
3. **Baisse du score** : `Score = (mutants détectés / mutants totaux)` diminue

---

## Captures d'écran

### Build réussi - Aucune régression détectée

<!-- Insérer capture d'écran ici -->

![Build Success](path/to/success-screenshot.png)

_Score de mutation stable ou amélioré → ✅ Build passes_

---

### Build échoué - Régression détectée

<!-- Insérer capture d'écran ici -->

![Build Failed](path/to/failure-screenshot.png)

_Baisse du mutation score après décommenter les méthodes → ❌ Build fails_

---

## Humour : Rickroll de débogage

En cas d'échec du build, une étape "Suggested Solution" s'affiche :

```yaml
- name: Suggested Solution
  if: failure()
  run: |
      echo "To learn more about the errors and how to fix them"
      echo "Visit the site https://tinyurl.com/43cts52a"
```

**Message affiché :**

> "Pour en apprendre plus sur les erreurs et comment les corriger, visitez https://tinyurl.com/43cts52a"

---

## Note importante

Pour les gros modules comme **core** et **web**, seule une partie des classes a été sélectionnée (ou le nombre de mutation par classes a été limité) pour l'exécution de Pitest. Cette décision a été prise à des fins de test et pour faciliter la correction, car l'analyse complète de tous les modules peut prendre jusqu'à **30 minutes**. Cette approche permet de démontrer le fonctionnement du système de mutation testing tout en maintenant des temps d'exécution raisonnables dans le cadre du workflow CI/CD.

## Test mockito

Ce test se trouve dans la classe
suivante : [GHUtilityMockitoTest](core/src/test/java/com/graphhopper/util/GHUtilityMockitoTest.java).
Les choix faits pour ce test se trouvent dans la documentation au sein de la classe et de ses
fonctions.

Cette classe a été créé pour tester la classe [GHUtility](core/src/main/java/com/graphhopper/util/GHUtility.java).
Ce choix s'explique par la continuité, cette classe ayant déjà été testée dans la tâche 2.
