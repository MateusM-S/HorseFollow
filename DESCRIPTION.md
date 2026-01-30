<h2>Objective</h2>
<p>Create a functional plugin that introduces a <strong>taming-like mechanic</strong> for mounts (horses and rams), a feature not yet available in the base game.</p>
<hr>
<h2>What it does</h2>
<p>This plugin creates a <strong>persistent bond</strong> between the player and a mount.</p>
<ul>
<li>Supports <strong>horses</strong> and <strong>rams</strong></li>
<li>Binding is done using <strong>feed items</strong> (Horse Feed / Ram Feed)</li>
<li>Once bound, the mount actively follows its owner</li>
<li>Follow distance and teleport behavior are configurable; automatic teleport can be fully disabled</li>
<li>The mount can be ordered to <strong>stay</strong> in place</li>
<li>You can <strong>call</strong> the bound mount using the Horn item or the <code>/horsefollow call</code> command</li>
</ul>
<p>The goal is a <strong>simple, functional, and reliable taming system</strong>, without complex AI or unnecessary mechanics.</p>
<p>Interaction is centered on <strong>items</strong>: feed to tame, horn to call. Commands are mainly used for configuration, status, and optional actions.</p>
<hr>
<h2>How to use</h2>
<ul>
<li>Obtain the appropriate <strong>feed</strong> (Horse Feed for horses, Ram Feed for rams)</li>
<li>Use the feed on the mount, or hold the feed and press <strong>F</strong> near the mount to create the bond</li>
<li>Once bound, the mount will automatically follow you (including walk/run animations)</li>
<li>Use the <strong>Horn</strong> item or <code>/horsefollow call</code> to call the mount</li>
<li>Use <code>/horsefollow stay</code> to make the mount stay in place</li>
<li>Configure follow distance with <code>/horsefollow distance &lt;value&gt;</code> (0 disables automatic teleport)</li>
</ul>
<hr>
<h2>Commands</h2>
<p><code>/horsefollow bind</code> &mdash; Binds the target mount (operator-only).</p>
<p><code>/horsefollow unbind</code> &mdash; Removes the active bond.</p>
<p><code>/horsefollow status</code> &mdash; Displays the current bond status.</p>
<p><code>/horsefollow distance &lt;value&gt;</code> &mdash; Sets teleport distance (0 disables automatic teleport).</p>
<p><code>/horsefollow call</code> &mdash; Calls the bound mount.</p>
<p><code>/horsefollow stay</code> &mdash; Makes the mount stay in place and stop following.</p>
<p><code>/horsefollow reload</code> &mdash; Reloads configuration and language files.</p>
<hr>
<h2><br>Portugu&ecirc;s</h2>
<div class="spoiler">
<h2>O que faz</h2>
<p>Este plugin cria um <strong>v&iacute;nculo persistente</strong> entre o jogador e sua montaria.</p>
<ul>
<li>A montaria fica vinculada ao jogador</li>
<li>Ap&oacute;s o v&iacute;nculo, a montaria passa a seguir o jogador</li>
<li>A dist&acirc;ncia de follow e o teleporte s&atilde;o configur&aacute;veis</li>
<li>O teleporte autom&aacute;tico pode ser totalmente desativado</li>
<li>A montaria pode ser instru&iacute;da a permanecer parada</li>
</ul>
<p>&Eacute; um sistema de domestica&ccedil;&atilde;o <strong>simples, funcional e confi&aacute;vel</strong>, sem IA complexa ou mec&acirc;nicas desnecess&aacute;rias.</p>
<p>O mod est&aacute; em transi&ccedil;&atilde;o para reduzir o uso de comandos.<br>A intera&ccedil;&atilde;o principal ser&aacute; feita por <strong>itens</strong>, ficando os comandos apenas para configura&ccedil;&atilde;o.</p>
<hr>
<h2><br>Como usar</h2>
<ul>
<li>Monte no cavalo</li>
<li>Use o comando de bind enquanto estiver montado</li>
<li>Ap&oacute;s o v&iacute;nculo, a montaria passa a te seguir automaticamente</li>
<li>Use o comando stay para fazer a montaria permanecer no local</li>
<li><strong>O teleporte autom&aacute;tico depende da dist&acirc;ncia configurada</strong></li>
</ul>
<h2><strong>Comandos</strong></h2>
<p><code>/horsefollow vincular</code> &ndash; Cria um v&iacute;nculo com a montaria (Somente adms).</p>
<p><code>/horsefollow desvincular</code> &ndash; Remove o v&iacute;nculo ativo.</p>
<p><code>/horsefollow status</code> &ndash; Mostra se h&aacute; v&iacute;nculo ativo.</p>
<p><code>/horsefollow distancia &lt;valor&gt;</code> &ndash; Define a dist&acirc;ncia do teleporte (0 desativa o teleporte autom&aacute;tico).</p>
<p><code>/horsefollow chamar</code> &ndash; Chama a montaria vinculada.</p>
<p><code>/horsefollow ficar</code> &ndash; Faz a montaria permanecer no local.</p>
<p><code>/horsefollow resetar</code> &ndash; Recarrega as configura&ccedil;&otilde;es e linguagem.</p>
</div>
<hr>
<h2><span style="color: #e67e23;"><br>Support &amp; Patreon</span></h2>
<p>I am currently unemployed and transitioning from an administrative role to programming. This is one of my first projects in this area.</p>
<p>The code may:</p>
<ul>
<li>Not be fully clean yet</li>
<li>Contain inconsistencies</li>
<li>Have excessive comments</li>
</ul>
<p>This is intentional and part of my learning process.</p>
<p>Any feedback is welcome.<br>Support development via Patreon:</p>
<p>👉 <a href="https://www.patreon.com/15452282/join" target="_blank" rel="nofollow noopener">Patreon</a></p>
<hr>
<h2>Links</h2>
<p><a href="https://github.com/MateusM-S/" target="_blank" rel="nofollow noopener"><img src="https://media.forgecdn.net/attachments/description/1440910/description_c6d9693b-ada8-4458-aa79-e88b2966948d.png" alt="" width="24" height="24">GitHub</a></p>
<p><a href="https://www.patreon.com/15452282/join" target="_blank" rel="nofollow noopener"><img src="https://media.forgecdn.net/attachments/description/1440910/description_43978c15-83c0-4928-8b60-576516bd2885.png" alt="" width="24" height="24">Patreon</a></p>
<p>&nbsp;</p>