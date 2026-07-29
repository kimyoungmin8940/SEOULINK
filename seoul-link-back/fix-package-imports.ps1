$sourceRoot = Join-Path $PSScriptRoot 'src/main/java'
$files = Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter *.java
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
foreach ($file in $files) {
    $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding utf8
    $expectedPackage = $file.DirectoryName.Substring($sourceRoot.Length + 1).Replace('\', '.')
    $text = [regex]::Replace($text, '(?m)^package\s+[\w.]+;', "package $expectedPackage;", 1)
    [System.IO.File]::WriteAllText($file.FullName, $text, $utf8NoBom)
}
$types = @{}
foreach ($file in $files) {
    $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding utf8
    $package = [regex]::Match($text, '(?m)^package\s+([\w.]+);').Groups[1].Value
    $type = [regex]::Match($text, '(?m)^\s*public\s+(?:class|interface|record|enum|@interface)\s+(\w+)').Groups[1].Value
    if ($package -and $type) {
        if (-not $types.ContainsKey($type)) { $types[$type] = @() }
        $types[$type] += "$package.$type"
    }
}

foreach ($file in $files) {
    $text = Get-Content -LiteralPath $file.FullName -Raw -Encoding utf8
    $package = [regex]::Match($text, '(?m)^package\s+([\w.]+);').Groups[1].Value
    $declaredType = [regex]::Match($text, '(?m)^\s*public\s+(?:class|interface|record|enum|@interface)\s+(\w+)').Groups[1].Value
    $imports = [regex]::Matches($text, '(?m)^import\s+([\w.]+);')
    foreach ($match in $imports) {
        $oldFqn = $match.Groups[1].Value
        $simpleName = $oldFqn.Substring($oldFqn.LastIndexOf('.') + 1)
        if ($types.ContainsKey($simpleName) -and $types[$simpleName].Count -eq 1 -and $types[$simpleName][0] -ne $oldFqn) {
            $text = $text.Replace("import $oldFqn;", "import $($types[$simpleName][0]);")
        }
    }
    $existing = @([regex]::Matches($text, '(?m)^import\s+([\w.]+);') | ForEach-Object { $_.Groups[1].Value })
    $needed = @()
    foreach ($type in $types.Keys) {
        if ($type -eq $declaredType -or $types[$type].Count -ne 1) { continue }
        $fqn = $types[$type][0]
        if ($fqn.StartsWith("$package.") -or $existing -contains $fqn) { continue }
        if ([regex]::IsMatch($text, "\\b$([regex]::Escape($type))\\b")) { $needed += $fqn }
    }
    if ($needed.Count -gt 0) {
        $insertion = ($needed | Sort-Object | ForEach-Object { "import $_;" }) -join "`r`n"
        $text = [regex]::Replace($text, '(?m)^(package\s+[\w.]+;\r?\n)', "`$1`r`n$insertion`r`n", 1)
    }
    [System.IO.File]::WriteAllText($file.FullName, $text, $utf8NoBom)
}
