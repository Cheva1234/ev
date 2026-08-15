# Implementation Plan: AI Calculus Tool

## Overview

Extend the existing MATH tool with a small, safe calculus engine that the AI can call and that returns LaTeX-formatted results for the chat UI.

## Scope

- Symbolic derivatives for the supported elementary expression grammar.
- Common symbolic antiderivatives; unsupported forms return a clear error.
- Numerical definite integrals using deterministic Simpson integration.
- Numerical two-sided limits where the sampled values converge.
- LaTeX output in direct tool results and tool details.
- LaTeX-safe chat and console presentation paths.
- Agent usage examples so Qwen can select the calculus operations.

## Architecture Decisions

- Keep the public tool family as `MATH` for compatibility with the existing agent loop.
- Add a shared calculus AST/parser/formatter instead of teaching the model an unstructured result format.
- Use the calculus parser/AST for sampled definite integrals and limits so implicit multiplication and functions follow the same grammar as symbolic operations. Keep the existing numeric evaluator variable-aware for ordinary numeric expressions.
- Use explicit command forms: `diff(expr,var)`, `integrate(expr,var)`, `integral(expr,a,b,var)`, and `limit(expr,var,point)`.
- Accept inferred-variable derivative/antiderivative calls when the local model omits `,x`.
- Return successful calculus LaTeX directly from the agent loop so the local model cannot corrupt structured math output.

## Verification

- Unit tests for parsing, derivatives, antiderivatives, definite integrals, limits, and LaTeX.
- Existing agent and math tests remain green.
- APK build succeeds.
- On-device AI tool test produces a clean LaTeX calculus result.

## Risks

- Symbolic integration is inherently incomplete; unsupported expressions must fail honestly.
- Numerical limits and integrals depend on finite, well-behaved sampled values.
- The current Android renderer colors LaTeX source rather than typesetting it; the tool will still emit valid LaTeX delimiters.
