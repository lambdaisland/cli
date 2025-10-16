#autoload

_licli() {
    local completion_output
    completion_output="$( ${words[1]} __licli completions -- "${words[@]}" )"

    if [ -n "$completion_output" ]; then
        local -a pairs
        pairs=("${(@f)${completion_output}}")
        _describe "${words[1]}" pairs
    fi

    return 0
}
