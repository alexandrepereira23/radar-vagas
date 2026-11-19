import { Pipe, PipeTransform } from '@angular/core';

const dataHora = new Intl.DateTimeFormat('pt-BR', {
  timeZone: 'America/Sao_Paulo',
  dateStyle: 'short',
  timeStyle: 'short',
  hourCycle: 'h23'
});

@Pipe({ name: 'saoPauloData' })
export class SaoPauloDataPipe implements PipeTransform {
  transform(iso: string): string {
    const instante = Date.parse(iso);
    return Number.isFinite(instante) ? dataHora.format(instante) : '—';
  }
}
