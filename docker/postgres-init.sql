-- One role and one schema per server, as an operator sets them up; the servers create their tables themselves.
CREATE ROLE l2nx_login LOGIN PASSWORD 'l2nx';
CREATE SCHEMA login AUTHORIZATION l2nx_login;

CREATE ROLE l2nx_game LOGIN PASSWORD 'l2nx';
CREATE SCHEMA game AUTHORIZATION l2nx_game;
