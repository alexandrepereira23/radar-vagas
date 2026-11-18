-- Migração V2: Suporte a múltiplas fontes, registro de estado de validação/bloqueio e código de requisição (Etapa 2)

ALTER TABLE configuracao_fonte ADD COLUMN categoria VARCHAR(50) NOT NULL DEFAULT 'COMUNIDADE';
ALTER TABLE configuracao_fonte ADD COLUMN estado_validacao VARCHAR(50) NOT NULL DEFAULT 'ATIVA_VALIDADA';
ALTER TABLE configuracao_fonte ADD COLUMN metodo_acesso VARCHAR(255);
ALTER TABLE configuracao_fonte ADD COLUMN credencial_necessaria VARCHAR(150);
ALTER TABLE configuracao_fonte ADD COLUMN observacao_validacao VARCHAR(1000);

ALTER TABLE oportunidade ADD COLUMN codigo_requisicao VARCHAR(150);
ALTER TABLE anuncio_origem ADD COLUMN codigo_requisicao VARCHAR(150);

CREATE INDEX idx_oportunidade_fonte_principal ON oportunidade(fonte_principal);
