#!/usr/bin/env bash
# Verification locale parallele, calquee sur la CI (.github/workflows/ci.yml).
#
#   1. build une seule fois : ./mvnw verify -DskipTests (compilation, jar, spotless ; aucun test),
#      pour que les processus suivants ne se marchent pas dessus dans target/ ;
#   2. en parallele, un processus Maven par voie, sur les classes deja compilees :
#        unit            surefire:test (+ tools/seed, comme le job unit de la CI)
#        it-<tranche>    failsafe:integration-test failsafe:verify -Dit.test=<motifs>
#      Les tranches viennent de tools/it-slices.txt (source unique, lue aussi par ci.yml) ; chaque
#      tranche ecrit ses rapports dans target/failsafe-reports/<tranche>/ (propriete
#      failsafe.reportsDirectory du pom) et demarre ses propres conteneurs Testcontainers ;
#   3. resume par voie (Tests run / Failures / Errors / Skipped / duree) ; code de sortie non nul
#      si une voie echoue, avec la commande pour relancer seuls les tests rouges.
#
# Usage :
#   tools/verify-fast.sh                      lance tout (make verify-fast)
#   tools/verify-fast.sh --patterns <tranche> affiche les motifs -Dit.test d'une tranche (ci.yml)
#   tools/verify-fast.sh --check              verifie it-slices.txt : chaque *IT.java hors perf dans
#                                             exactement une tranche, ci.yml liste ces tranches
#
# `./mvnw verify` reste la commande de reference avant toute PR : ce script ne change ni les tests,
# ni leur selection, ni la configuration Maven ; il ne fait que les repartir.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
slices_file="$root/tools/it-slices.txt"
ci_file="$root/.github/workflows/ci.yml"
out_dir="$root/target/verify-fast"

die() { echo "verify-fast : $*" >&2; exit 2; }

# --- tranches -------------------------------------------------------------------------------------

slice_lines() { grep -Ev '^[[:space:]]*(#|$)' "$slices_file" | tr -d '\r'; }
slice_names() { slice_lines | cut -d= -f1; }
slice_patterns() {
  local line
  line="$(slice_lines | grep -E "^$1=" || true)"
  [ -n "$line" ] || die "tranche inconnue : '$1' (voir $slices_file)"
  printf '%s\n' "${line#*=}"
}

# Motif surefire (chemin Ant : `**/` = zero ou plusieurs dossiers, `*` = sans `/`) -> regex ERE.
glob_to_regex() {
  local p="$1"
  p="${p//./\\.}"
  p="${p//\/\*\*/@TAIL@}"   # `/**` en fin : tout sous-arbre
  p="${p//\*\*\//@DIRS@}"   # `**/`      : zero ou plusieurs dossiers
  p="${p//\*/@STAR@}"       # `*`        : sans separateur
  p="${p//@TAIL@/(/.*)?}"
  p="${p//@DIRS@/(.*/)?}"
  p="${p//@STAR@/[^/]*}"
  printf '^%s$\n' "$p"
}

# Vrai si le chemin (com/psymate/.../FooIT.java) est selectionne par la liste de motifs.
matches_slice() {
  local path="$1" patterns="$2" pattern included=false
  IFS=',' read -r -a list <<< "$patterns"
  for pattern in "${list[@]}"; do
    if [[ "$pattern" == !* ]]; then
      if [[ "$path" =~ $(glob_to_regex "${pattern#!}") ]]; then return 1; fi
    elif [[ "$path" =~ $(glob_to_regex "$pattern") ]]; then
      included=true
    fi
  done
  $included
}

check() {
  local errors=0 names name path count where
  names="$(slice_names)"
  [ -n "$names" ] || die "aucune tranche dans $slices_file"
  if [ "$(printf '%s\n' "$names" | sort | uniq -d | wc -l)" -ne 0 ]; then
    echo "it-slices.txt : nom de tranche en double" >&2; errors=1
  fi
  # 1. ci.yml liste exactement ces tranches (matrice `slice: [a, b, c]`).
  local ci_names
  ci_names="$(grep -E '^[[:space:]]*slice:[[:space:]]*\[' "$ci_file" | sed -E 's/.*\[//; s/\].*//; s/[[:space:]]//g' | tr ',' '\n' || true)"
  if [ "$(printf '%s\n' "$names" | sort)" != "$(printf '%s\n' "$ci_names" | sort)" ]; then
    echo "ci.yml et tools/it-slices.txt divergent : tranches CI = [$(printf '%s' "$ci_names" | paste -sd, -)], fichier = [$(printf '%s' "$names" | paste -sd, -)]" >&2
    errors=1
  fi
  # 2. chaque *IT.java hors perf appartient a exactement une tranche.
  while IFS= read -r path; do
    count=0; where=""
    for name in $names; do
      if matches_slice "$path" "$(slice_patterns "$name")"; then count=$((count + 1)); where="$where $name"; fi
    done
    if [ "$count" -eq 0 ]; then echo "non couvert par it-slices.txt : $path" >&2; errors=1
    elif [ "$count" -gt 1 ]; then echo "dans plusieurs tranches ($where ) : $path" >&2; errors=1; fi
  done < <(cd "$root/src/test/java" && find . -name '*IT.java' -not -path '*/perf/*' | sed 's#^\./##' | sort)
  if [ "$errors" -ne 0 ]; then echo "verify-fast --check : KO" >&2; exit 1; fi
  echo "verify-fast --check : OK ($(printf '%s\n' "$names" | wc -l | tr -d ' ') tranches, chaque *IT.java hors perf dans exactement une tranche, ci.yml coherent)"
}

# --- lancement ------------------------------------------------------------------------------------

fmt_duration() { printf '%dm%02ds' $(( $1 / 60 )) $(( $1 % 60 )); }

declare -A pids
declare -a lanes=()

start_lane() {
  local name="$1"; shift
  lanes+=("$name")
  (
    local t0 rc
    t0=$(date +%s)
    set +e
    "$@" > "$out_dir/$name.log" 2>&1
    rc=$?
    echo "$rc $(( $(date +%s) - t0 ))" > "$out_dir/$name.rc"
  ) &
  pids["$name"]=$!
}

run() {
  cd "$root"
  mkdir -p "$out_dir"
  rm -f "$out_dir"/*.log "$out_dir"/*.rc
  local t_start t_build names n
  t_start=$(date +%s)
  names="$(slice_names)"
  n=$(printf '%s\n' "$names" | wc -l | tr -d ' ')

  check

  echo "[1/3] Build unique : ./mvnw verify -DskipTests (compilation, jar, spotless)"
  if ! ./mvnw -B verify -DskipTests > "$out_dir/build.log" 2>&1; then
    tail -n 40 "$out_dir/build.log"
    die "build en echec (journal : target/verify-fast/build.log)"
  fi
  t_build=$(date +%s)
  echo "      termine en $(fmt_duration $(( t_build - t_start )))"

  echo "[2/3] Tests en parallele : unit + $n tranches d'IT ($(printf '%s' "$names" | paste -sd' ' -)) ; journaux dans target/verify-fast/"
  trap 'echo; echo "interruption : arret des voies"; kill "${pids[@]}" 2>/dev/null; exit 130' INT TERM
  start_lane unit bash -c './mvnw -B surefire:test && ./mvnw -B -q -f tools/seed/pom.xml verify'
  local name
  for name in $names; do
    start_lane "it-$name" ./mvnw -B failsafe:integration-test failsafe:verify \
      -Dit.test="$(slice_patterns "$name")" \
      -Dfailsafe.reportsDirectory="target/failsafe-reports/$name"
  done

  local pending=("${lanes[@]}") still rc dur
  while [ "${#pending[@]}" -gt 0 ]; do
    wait -n 2>/dev/null || true
    still=()
    for name in "${pending[@]}"; do
      if [ -f "$out_dir/$name.rc" ]; then
        read -r rc dur < "$out_dir/$name.rc"
        if [ "$rc" -eq 0 ]; then echo "      ok   $name ($(fmt_duration "$dur"))"
        else echo "      KO   $name ($(fmt_duration "$dur"), code $rc)"; fi
      else
        still+=("$name")
      fi
    done
    pending=("${still[@]+"${still[@]}"}")
  done
  trap - INT TERM

  echo "[3/3] Resume"
  local failed=0 totals line
  printf '      %-14s %-5s %10s %9s %7s %8s %7s\n' voie etat "Tests run" Failures Errors Skipped duree
  for name in "${lanes[@]}"; do
    read -r rc dur < "$out_dir/$name.rc"
    # derniere ligne de total surefire/failsafe (sans " -- in <classe>")
    line="$(tr -d '\r' < "$out_dir/$name.log" | grep -E 'Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$' | tail -n 1 || true)"
    totals="$(printf '%s' "$line" | grep -oE '[0-9]+' | paste -sd' ' - || true)"
    [ -n "$totals" ] || totals="? ? ? ?"
    # shellcheck disable=SC2086
    printf '      %-14s %-5s %10s %9s %7s %8s %7s\n' "$name" "$([ "$rc" -eq 0 ] && echo ok || echo KO)" $totals "$(fmt_duration "$dur")"
    [ "$rc" -eq 0 ] || failed=1
  done
  echo "      total : $(fmt_duration $(( $(date +%s) - t_start ))) (build $(fmt_duration $(( t_build - t_start ))), tests $(fmt_duration $(( $(date +%s) - t_build ))))"

  if [ "$failed" -ne 0 ]; then
    echo
    echo "Tests en echec (journal : target/verify-fast/<voie>.log) :"
    local classes=""
    for name in "${lanes[@]}"; do
      read -r rc dur < "$out_dir/$name.rc"
      [ "$rc" -eq 0 ] && continue
      line="$(tr -d '\r' < "$out_dir/$name.log" | grep -E '<<< (FAILURE|ERROR)! -- in ' | sed -E 's/.* -- in //' | sort -u | paste -sd, - || true)"
      if [ -n "$line" ]; then echo "  $name : $line"; classes="${classes:+$classes,}$line"
      else echo "  $name : pas de total de tests (voir le journal, code $rc)"; fi
    done
    if [ -n "$classes" ]; then
      echo "A relancer seuls (un test de temps de reponse peut souffrir de la charge en parallele) :"
      echo "  ./mvnw verify -Dit.test=$classes -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false"
    fi
    exit 1
  fi
  echo "verify-fast : OK"
}

case "${1:-}" in
  "") run ;;
  --check) check ;;
  --patterns) [ -n "${2:-}" ] || die "usage : tools/verify-fast.sh --patterns <tranche>"; slice_patterns "$2" ;;
  -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
  *) die "argument inconnu : '$1' (voir --help)" ;;
esac
