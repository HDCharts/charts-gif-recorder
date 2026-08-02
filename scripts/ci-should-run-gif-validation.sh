#!/usr/bin/env bash
set -euo pipefail

is_gif_validation_path() {
  local changed_file="$1"

  case "$changed_file" in
    app/*|gif-baselines/*|gradle/libs.versions.toml|gradle/wrapper/*|gradlew|gradlew.bat|build.gradle.kts|settings.gradle.kts|gradle.properties|.github/workflows/validate-gifs.yml)
      return 0
      ;;
    lib/recorder-*/src/*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_runtime_build_change() {
  local base_sha="$1"
  local head_sha="$2"
  local changed_file="$3"

  git diff --unified=0 "${base_sha}...${head_sha}" -- "$changed_file" \
    | rg -q '^[+-][^+-].*(implementation|api\b|compileOnly|runtimeOnly|debugImplementation|androidTestImplementation|testImplementation|kapt|ksp|plugins|version\.ref|\bversion\b|compileSdk|minSdk|targetSdk|buildFeatures|jvmTarget|compose|sourceSets)'
}

is_gif_validation_change() {
  local changed_files="$1"
  local changed_file

  while IFS= read -r changed_file; do
    [[ -n "$changed_file" ]] || continue
    if is_gif_validation_path "$changed_file"; then
      echo "true"
      return
    fi
  done <<<"$changed_files"

  echo "false"
}

assert_equal() {
  local expected="$1"
  local actual="$2"
  local name="$3"

  if [[ "$expected" != "$actual" ]]; then
    echo "FAIL: $name (expected '$expected', got '$actual')" >&2
    return 1
  fi

  echo "PASS: $name"
}

run_self_test() {
  local failures=0
  local result

  result="$(is_gif_validation_change $'README.md\ndocs/README.md\nlib/recorder-android/build.gradle.kts')"
  if ! assert_equal "false" "$result" "docs and Dokka-only changes"; then
    failures=$((failures + 1))
  fi

  result="$(is_gif_validation_change "app/src/main/java/com/example/Demo.kt")"
  if ! assert_equal "true" "$result" "app source change"; then
    failures=$((failures + 1))
  fi

  result="$(is_gif_validation_change "lib/recorder-core/src/main/kotlin/Foo.kt")"
  if ! assert_equal "true" "$result" "recorder source change"; then
    failures=$((failures + 1))
  fi

  result="$(is_gif_validation_change "gradle/libs.versions.toml")"
  if ! assert_equal "true" "$result" "dependency catalog change"; then
    failures=$((failures + 1))
  fi

  result="$(is_gif_validation_change "gif-baselines/PieChartDemo.gif")"
  if ! assert_equal "true" "$result" "GIF baseline change"; then
    failures=$((failures + 1))
  fi

  result="$(is_gif_validation_change ".github/workflows/validate-gifs.yml")"
  if ! assert_equal "true" "$result" "GIF workflow change"; then
    failures=$((failures + 1))
  fi

  if [[ "$failures" -gt 0 ]]; then
    echo "Self-test failed: $failures case(s)." >&2
    return 1
  fi

  echo "All self-tests passed."
}

main() {
  if [[ "${1:-}" == "--self-test" ]]; then
    run_self_test
    return
  fi

  local base_sha="${1:?base sha is required}"
  local head_sha="${2:?head sha is required}"
  local changed_files
  local changed_file

  changed_files="$(git diff --name-only "${base_sha}...${head_sha}")"

  while IFS= read -r changed_file; do
    [[ -n "$changed_file" ]] || continue
    if is_gif_validation_path "$changed_file"; then
      echo "true"
      return
    fi
    case "$changed_file" in
      lib/*/build.gradle.kts|lib/build.gradle.kts|lib/settings.gradle.kts)
        if is_runtime_build_change "$base_sha" "$head_sha" "$changed_file"; then
          echo "true"
          return
        fi
        ;;
    esac
  done <<<"$changed_files"

  echo "false"
}

main "$@"
