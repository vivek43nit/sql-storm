{{/*
Expand the name of the chart.
*/}}
{{- define "fkblitz.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "fkblitz.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Chart label: name-version
*/}}
{{- define "fkblitz.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "fkblitz.labels" -}}
helm.sh/chart: {{ include "fkblitz.chart" . }}
{{ include "fkblitz.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "fkblitz.selectorLabels" -}}
app.kubernetes.io/name: {{ include "fkblitz.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Service account name
*/}}
{{- define "fkblitz.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "fkblitz.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Name of the secret that holds sensitive values.
*/}}
{{- define "fkblitz.secretName" -}}
{{- if .Values.secret.existingSecret }}
{{- .Values.secret.existingSecret }}
{{- else }}
{{- include "fkblitz.fullname" . }}
{{- end }}
{{- end }}

{{/*
Internal Redis service name (used as host when redis.host is unset).
*/}}
{{- define "fkblitz.redisHost" -}}
{{- if .Values.redis.host }}
{{- .Values.redis.host }}
{{- else }}
{{- printf "%s-redis" (include "fkblitz.fullname" .) }}
{{- end }}
{{- end }}
