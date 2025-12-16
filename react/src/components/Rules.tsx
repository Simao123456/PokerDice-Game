export function Rules() {
  return (
    <div className="rules-container">
      <h2>Regras do Poker Dice Game</h2>

      <section className="rules-section">
        <h3>Objetivo do Jogo</h3>
        <p>
          O Poker Dice Game é jogado com dados de poker especiais que contêm:
          Ás, Rei, Dama, Valete, 10 e 9. O objetivo é formar a melhor mão
          possível com cinco dados e vencer as rodadas para acumular moedas.
        </p>
      </section>

      <section className="rules-section">
        <h3>Como Jogar</h3>

        <h4>Configuração da Partida</h4>
        <ul>
          <li>Podes criar um novo lobby ou juntar-te a um existente</li>
          <li>
            O criador do lobby define: número de jogadores, número de rodadas e
            outras configurações
          </li>
          <li>
            A partida começa quando o lobby está cheio ou quando o tempo limite
            expira (com pelo menos o mínimo de jogadores)
          </li>
        </ul>

        <h4>Durante as Rodadas</h4>
        <ol>
          <li>
            Cada rodada começa com todos os jogadores a pagar uma ante (ex: 1
            moeda)
          </li>
          <li>Na tua vez, lança todos os cinco dados</li>
          <li>
            Podes escolher quais dados manter e relançar os restantes (até 2
            vezes)
          </li>
          <li>Tens um máximo de 3 lançamentos por turno</li>
          <li>Após todos jogarem, as mãos são comparadas</li>
          <li>O jogador com a melhor mão ganha o pot (todas as antes)</li>
          <li>
            Jogadores sem moedas suficientes para pagar a ante são excluídos
          </li>
        </ol>

        <h4>Fim da Partida</h4>
        <p>
          A partida termina quando todas as rodadas são completadas ou quando
          apenas um jogador consegue pagar a ante.
        </p>
      </section>
    </div>
  );
}
