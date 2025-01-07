    create database pasteinsdb;

    CREATE OR REPLACE FUNCTION stringify_bigint(n bigint) RETURNS text AS
    '
	DECLARE
    alphabet text:=''abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'';
    base int:=length(alphabet);
    _n bigint:=abs(n);
    output text:='''';
    BEGIN
    LOOP
        output := output || substr(alphabet, 1+(_n%base)::int, 1);
        _n := _n / base;
        EXIT WHEN _n=0;
    END LOOP;
    RETURN output;
    END;
    '
	LANGUAGE plpgsql strict immutable;

    CREATE OR REPLACE FUNCTION pseudo_encrypt(VALUE bigint) returns bigint AS
    '
    DECLARE
    l1 bigint;
    l2 bigint;
    r1 bigint;
    r2 bigint;
    i int:=0;
    BEGIN
        l1:= (VALUE >> 32) & 4294967295::bigint;
        r1:= VALUE & 4294967295;
        WHILE i < 3 LOOP
            l2 := r1;
            r2 := l1 # ((((1366.0 * r1 + 150889) % 714025) / 714025.0) * 32767*32767)::int;
            l1 := l2;
            r1 := r2;
            i := i + 1;
        END LOOP;
    RETURN ((l1::bigint << 32) + r1);
    END;
    '
    LANGUAGE plpgsql strict immutable;

	CREATE SEQUENCE link_id;
    CREATE SEQUENCE access_token_init;
    CREATE TABLE PASTEINS(
        id serial not null,
        PRIMARY KEY(id),
        shorthand character varying NOT NULL DEFAULT stringify_bigint(pseudo_encrypt(nextval('link_id'))),
        last_access timestamp NOT NULL DEFAULT NOW(),
        body text,
        access_token character varying NOT NULL
    );