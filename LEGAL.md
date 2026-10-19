# Legal notice

This page explains what nx-l2 is, why it is lawful to develop and publish it, what the repository does not contain and
how to report a problem. It is not legal advice. Laws differ between countries; anyone who runs a public server should
consult a qualified lawyer first.

## 1. What the project is

nx-l2 is an independent, non-commercial, open-source server implementation built for education, research,
interoperability testing and preservation. Its public test server is a demo and a test bed for the project's own
integrations, not a game service.

The project itself:

- sells nothing: no builds, no items, no services, no paid access;
- accepts no donations;
- carries no advertising and no links to commercial servers;
- does not encourage or support commercial operation of servers built from it.

The licenses (Apache 2.0, MIT, GPLv3 per file; see [README](README.md#license)) grant everyone the right to use, modify
and distribute the code. Those rights cannot be restricted by this page, and this page does not try to. It states the
project's purpose and policy, not a license term.

## 2. Interoperability

The server is independent software written to interoperate with a game client the user already owns. Building such
software is protected in the main jurisdictions:

- **European Union:** Directive 2009/24/EC, Article 5(3) (observing, studying and testing a program to understand its
  ideas and principles) and Article 6 (decompilation for interoperability), implemented in every member state (for
  Poland, Articles 75(2) and 75(3) of the Copyright Act). The Court of Justice held in _SAS Institute v World
  Programming_ (C-406/10, 2012) that the functionality of a program, its programming language and the format of its data
  files are not protected by copyright.
- **United States:** 17 U.S.C. § 102(b) excludes ideas, procedures, processes and methods of operation from copyright;
  17 U.S.C. § 1201(f) permits reverse engineering for interoperability. Courts upheld compatible implementations in
  _Sega Enterprises v. Accolade_ (9th Cir. 1992) and _Sony Computer Entertainment v. Connectix_ (9th Cir. 2000).

The project does not decompile, copy or redistribute any server software of a game publisher.

## 3. What the repository does not contain

- no game client and no part of it;
- no client assets: models, textures, sounds, music, artwork, interface;
- no geodata and no baked navigation meshes;
- no decompiled, leaked or proprietary code;
- no links to download any of the above.

Every operator prepares the data the server needs from a client they own, with the tools the project ships.

**Game data.** The server data describes rules: numeric values, formulas, identifiers, spawn points. Such functional
data is not protected expression. Texts the project ships are reviewed for whether they are original
([#9](https://github.com/l2nx/nx-l2/issues/9)).

## 4. Provenance

- The code base is the public open-source L2J Mobius project, refactored. `NOTICE` names the upstream and the commit the
  import started from.
- Every file states its license and copyright holder ([REUSE](https://reuse.software)); CI checks it.
- Contributions follow an originality policy ([CONTRIBUTING.md](CONTRIBUTING.md#originality)): behaviour may be
  re-implemented, code from proprietary sources is never accepted.

## 5. Trademarks

nx-l2 is not affiliated with, endorsed or sponsored by any game publisher or developer. All trademarks are the property
of their respective owners and are used only to describe compatibility. The project uses no publisher's trademark in its
name, logo or domain, and no publisher's artwork.

## 6. Terms of service

Game publishers' terms of service bind their own users. Running a server or connecting to one may conflict with such
terms. Each operator and user is responsible for the way they use the software and for compliance with the laws of their
country.

## 7. Notice and takedown

If you believe something in this repository or on the project's test server infringes your rights, tell us first: we act
faster than a platform-level process and remove only what is affected.

Send a notice to **support@l2nx.app** with the subject "Notice". It must contain:

1. your name, address, email and phone number; if you act on behalf of a rights holder, a statement to that effect;
2. a description of the work or other right you believe is infringed;
3. the exact location of the material: a link to the file, line or page;
4. an explanation of why the material infringes your rights;
5. a statement that you have a good faith belief that the use is not authorised by the rights holder, its agent or the
   law;
6. a statement that the information in the notice is accurate and that you are the rights holder or are authorised to
   act on its behalf;
7. your signature (your full name at the end of the email is sufficient).

What happens next:

- a person reviews the notice; incomplete notices may be returned for completion;
- if the notice is substantiated, the material is removed or disabled, and the notifier is informed of the outcome;
- the author of removed material may dispute the removal with a counter-notice to **support@l2nx.app** with the subject
  "Counter-notice", stating why the removal was a mistake.

A knowingly false notice may make the notifier liable. Notices may also be sent to the hosting platform under its own
procedure (for GitHub,
[its DMCA policy](https://docs.github.com/en/site-policy/content-removal-policies/dmca-takedown-policy)).

Security vulnerabilities: **security@l2nx.app**.

## 8. No warranty

The software is provided "as is", without warranty of any kind, as its licenses state. The project's developers are not
responsible for how others deploy or use it.
