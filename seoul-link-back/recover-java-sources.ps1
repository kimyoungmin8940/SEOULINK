$repoRoot = Split-Path $PSScriptRoot -Parent
$projectPrefix = 'seoul-link-back/'
$sourceRoot = Join-Path $PSScriptRoot 'src/main/java'
$backupRoot = Join-Path $PSScriptRoot '.codex-backup-before-source-recovery'
Copy-Item -LiteralPath $sourceRoot -Destination $backupRoot -Recurse -Force

$headFiles = @(git -C $repoRoot ls-tree -r --name-only HEAD -- "$($projectPrefix)src/main/java")
$byName = @{}
foreach ($headFile in $headFiles) {
    $name = Split-Path $headFile -Leaf
    if (-not $byName.ContainsKey($name)) { $byName[$name] = @() }
    $byName[$name] += $headFile
}

Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter *.java | ForEach-Object {
    $target = $_.FullName
    $relative = $target.Substring($repoRoot.Length + 1).Replace('\', '/')
    $source = $null
    if ($headFiles -contains $relative) {
        $source = $relative
    } elseif ($byName.ContainsKey($_.Name) -and $byName[$_.Name].Count -eq 1) {
        $source = $byName[$_.Name][0]
    }
    if ($source) {
        $escapedTarget = $target.Replace('"', '\"')
        cmd /c "git -C `"$repoRoot`" show HEAD:$source > `"$escapedTarget`""
    }
}
