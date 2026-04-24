branches=("feature/A-01-menu-platform-boundary" "feature/A-02-object-key-chain-dictionary" "feature/A-03-gis-coordinate-depth-freeze" "feature/A-04-rbac-data-topic-matrix" "feature/A-05-realtime-link-metric-spec" "feature/A-06-incident-workorder-state-machine" "feature/A-07-api-error-contract")

new_branches=()
existing_branches=()

for branch in "${branches[@]}"; do
    if git rev-parse --verify "$branch" >/dev/null 2>&1; then
        existing_branches+=("$branch")
        git push -u origin "$branch" --no-progress
    elif git rev-parse --verify "origin/$branch" >/dev/null 2>&1; then
        git checkout --track "origin/$branch"
        existing_branches+=("$branch")
    else
        git checkout -b "$branch" main
        git push -u origin "$branch" --no-progress
        new_branches+=("$branch")
    fi
done

git checkout main
echo "RESULT_NEW:${new_branches[@]}"
echo "RESULT_EXISTING:${existing_branches[@]}"
echo "RESULT_CURRENT:$(git branch --show-current)"
