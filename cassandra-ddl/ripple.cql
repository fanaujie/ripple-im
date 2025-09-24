-- Create ripple keyspace
CREATE KEYSPACE IF NOT EXISTS ripple
    WITH replication = {
        'class': 'SimpleStrategy',
        'replication_factor': 1
        };

USE ripple;
