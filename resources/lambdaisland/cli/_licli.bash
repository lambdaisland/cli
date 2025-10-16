# Bash completion function for lambdaisland/cli

_licli() {
    local cur prev words cword
    _init_completion || return

    # Get completion suggestions from the CLI tool
    local completion_output
    completion_output="$( ${words[0]} __licli completions -- "${words[@]}" 2>/dev/null )"

    if [ -n "$completion_output" ]; then
        # Parse the completion output (format: "option:description" or just "option")
        local -a completions
        local -A descriptions
        while IFS= read -r line; do
            # Extract option and description
            local option="${line%%:*}"
            local description="${line#*:}"
            completions+=( "$option" )
            descriptions["$option"]="$description"
        done <<< "$completion_output"

        # Generate completions with compgen
        COMPREPLY=($(compgen -W "${completions[*]}" -- "$cur"))

        # Show descriptions when multiple options exist
        if [ ${#COMPREPLY[@]} -gt 1 ]; then
            echo
            for option in "${COMPREPLY[@]}"; do
                printf "%-25s %s\n" "$option" "${descriptions[$option]}"
            done
            # Re-print the current command line
            printf "\n%s" "${COMP_LINE@P}"
        fi
    fi
}
