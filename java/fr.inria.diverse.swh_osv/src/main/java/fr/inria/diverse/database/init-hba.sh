#!/bin/bash
echo "host    all    all    172.17.0.0/16    md5" >> "$PGDATA/pg_hba.conf"
