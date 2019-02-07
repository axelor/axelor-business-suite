/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2019 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.account.service.move;

import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.db.repo.MoveLineRepository;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import java.util.List;

@Singleton
public class MoveRemoveService {

  protected MoveRepository moveRepo;

  protected MoveLineRepository moveLineRepo;

  @Inject
  public MoveRemoveService(MoveRepository moveRepo, MoveLineRepository moveLineRepo) {

    this.moveRepo = moveRepo;
    this.moveLineRepo = moveLineRepo;
  }

  @Transactional
  public void archiveMove(Move move) {
    moveRepo.remove(move);
    for (MoveLine moveLine : move.getMoveLineList()) {
      moveLineRepo.remove(moveLine);
    }
  }

  @Transactional
  public void deleteMultiple(List<? extends Move> moveList) {
    for (Move move : moveList) {
      this.archiveMove(move);
    }
  }
}
