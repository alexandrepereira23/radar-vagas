-- Migração V3: Continuação persistida de paginação/janela e separação entre configuração e validação por evidência

ALTER TABLE configuracao_fonte ADD COLUMN pagina_continuacao INTEGER;
ALTER TABLE configuracao_fonte ADD COLUMN cursor_janela_atual TIMESTAMP WITH TIME ZONE;
ALTER TABLE configuracao_fonte ADD COLUMN maior_atualizacao_janela TIMESTAMP WITH TIME ZONE;

UPDATE configuracao_fonte
   SET estado_validacao = 'CONFIGURADA_NAO_VALIDADA'
 WHERE estado_validacao = 'ATIVA_VALIDADA'
   AND ultima_coleta_sucesso_em IS NULL;

UPDATE configuracao_fonte
   SET estado_validacao = 'NAO_IMPLEMENTADA',
       ativo = FALSE
 WHERE fornecedor = 'JSEARCH_API';
