---
name: ✨ Feature
about: 새 기능을 제안합니다
title: "✨ feat: "
labels: feat
---

## Context
<!-- 왜 만드는지, 비즈니스 맥락. features.md / product.md 의 어느 영역인지 -->

## Scope

### In Scope
- 

### Out of Scope
- 

## 핵심 정책 결정
<!--
policy.md / product.md 의 미정 사항 + 영향도 높은 결정.
/impl 의 plan mode 합의 기준점이 됨.

영향도 체크리스트 (누락 점검):
- 다중성 / 카디널리티 (1:1 vs 1:N, 단일 vs 다중 선택)
- 권한 분기 (role 별 동작 차이 — 소비자 / 셀러 / 어드민)
- 인덱스 / 유니크 영향 (새 검색 패턴 / 이메일·닉네임 유니크 등)
- 마이그레이션 영향 (기존 테이블 NOT NULL 추가, FK 변경 등)
- Enum 후보 / 상태값 전체 (누락 시 분기 손실)
- 외부 시스템 의존 (외부 API / 이메일 / 알림 — Mock 여부 포함)

features.md / policy.md 와 충돌하거나 모호하면 임의 가정 X — 옵션 제시 후 사용자 확정.
-->
- 

## Business Logic (큰 그림)
<!-- 핵심 흐름 요약. 상세 설계는 /impl 의 plan mode 에서 합의 -->
- 
