/// Use cases for playing a game against a bot: create, play a move,
/// resign, and read one game or the list of games.
///
/// Each use case is an interface (the inbound port the adapters call) and a
/// plain class implementing it, built in `kopi-bootstrap` with its ports
/// (ADR-002). The interfaces also let bootstrap wrap the use cases in a
/// transaction without Spring entering this module.
package com.arquitechthor.kopi.application.game;
