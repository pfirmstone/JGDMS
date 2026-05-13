{{/*
JGDMS SCAP Helm chart — _helpers.tpl

Common template helpers shared across all templates.
*/}}

{{/*
Expand the namespace name.
*/}}
{{- define "jgdms-scap.namespace" -}}
{{- .Values.global.namespace | default "jgdms-scap" }}
{{- end }}

{{/*
Expand the trust domain.
*/}}
{{- define "jgdms-scap.trustDomain" -}}
{{- .Values.global.trustDomain | default "jgdms.example.org" }}
{{- end }}

{{/*
Lookup URL inside the cluster.
*/}}
{{- define "jgdms-scap.lookupUrl" -}}
{{- .Values.global.lookupUrl | default (printf "jini://lookup.%s.svc.cluster.local:4160" (include "jgdms-scap.namespace" .)) }}
{{- end }}

{{/*
Image reference helper: prepends registry prefix if set.
Usage: {{ include "jgdms-scap.image" (dict "repo" .Values.host1.image.repository "tag" .Values.host1.image.tag "global" .Values.global) }}
*/}}
{{- define "jgdms-scap.image" -}}
{{- $registry := .global.imageRegistry | default "" -}}
{{- $tag := .tag | default .global.jgdmsVersion -}}
{{- if $registry -}}
{{- printf "%s/%s:%s" $registry .repo $tag -}}
{{- else -}}
{{- printf "%s:%s" .repo $tag -}}
{{- end -}}
{{- end }}

{{/*
Common pod security context used by all SCAP hosts.
*/}}
{{- define "jgdms-scap.podSecurityContext" -}}
runAsNonRoot: true
runAsUser: 1000
runAsGroup: 1000
fsGroup: 1000
seccompProfile:
  type: RuntimeDefault
{{- end }}

{{/*
Common container security context.
*/}}
{{- define "jgdms-scap.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: ["ALL"]
{{- end }}

{{/*
SPIRE SVID hostPath volume definition.
*/}}
{{- define "jgdms-scap.spireVolume" -}}
- name: spire-svid
  hostPath:
    path: {{ .Values.spire.hostSvidPath | default "/run/spire/svid" }}
    type: Directory
{{- end }}

{{/*
SPIRE SVID volume mount definition.
*/}}
{{- define "jgdms-scap.spireVolumeMount" -}}
- name: spire-svid
  mountPath: /run/spire
  readOnly: true
{{- end }}

{{/*
Common environment variables for every SCAP pod.
*/}}
{{- define "jgdms-scap.commonEnv" -}}
- name: TRUST_DOMAIN
  value: {{ include "jgdms-scap.trustDomain" . | quote }}
- name: JGDMS_LOOKUP_URL
  value: {{ include "jgdms-scap.lookupUrl" . | quote }}
- name: SPIRE_DIR
  value: {{ .Values.global.spireDir | default "/run/spire" | quote }}
- name: JGDMS_VERSION
  value: {{ .Values.global.jgdmsVersion | quote }}
- name: JGDMS_SERVICE_HOST
  valueFrom:
    fieldRef:
      fieldPath: status.podIP
{{- end }}

{{/*
Standard TCP liveness probe on the given port.
*/}}
{{- define "jgdms-scap.livenessProbe" -}}
livenessProbe:
  tcpSocket:
    port: {{ . }}
  initialDelaySeconds: 30
  periodSeconds: 20
  failureThreshold: 3
{{- end }}

{{/*
Standard TCP readiness probe on the given port.
*/}}
{{- define "jgdms-scap.readinessProbe" -}}
readinessProbe:
  tcpSocket:
    port: {{ . }}
  initialDelaySeconds: 20
  periodSeconds: 10
{{- end }}
