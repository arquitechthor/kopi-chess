/// Use cases for post-game analysis (design §8): ask for a game's analysis,
/// and run the next queued one.
///
/// Running is slow (one engine search per position), so it never happens in
/// a request: [RequestAnalysisUseCase] only queues, and a worker in
/// `kopi-bootstrap` calls [RunAnalysisJobUseCase] in the background
/// (ADR-007). The judging itself is the domain's (`domain.analysis`); these
/// classes fetch what it needs and store what it says.
package com.arquitechthor.kopi.application.analysis;
