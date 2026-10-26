package com.arquitechthor.kopi.application.analysis;

/// Run the next queued analysis, if there is one. Called over and over by
/// the worker in `kopi-bootstrap`.
public interface RunAnalysisJobUseCase {

    /// Claims the oldest queued job and analyses its game: saved and `DONE`,
    /// back in the queue to try again, or `FAILED` with the reason. It
    /// throws nothing for a failed analysis: the job records it.
    ///
    /// @return whether there was a job (the worker waits a little when not)
    boolean runNext();
}
